package com.familyhub.service;

import com.familyhub.dto.gemini.GeminiReceiptResult;
import com.familyhub.exception.GeminiParsingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Thin wrapper around the Gemini REST API.
 * Sends a receipt image (as base64) with a structured prompt and returns
 * the extracted data as a {@link GeminiReceiptResult}.
 *
 * One API call does both OCR and category assignment — no second pass needed.
 */
@Slf4j
@Service
public class GeminiClient {

    // Prompt instructs Gemini to return strict JSON — every field and allowed category
    // is listed explicitly so the model doesn't invent its own structure.
    private static final String RECEIPT_PROMPT = """
            Analyze this receipt image and return ONLY valid JSON with no markdown formatting.

            Use this exact structure:
            {
              "vendorName": "string or null",
              "purchaseDate": "YYYY-MM-DD or null",
              "totalAmount": number or null,
              "items": [
                {
                  "productName": "string",
                  "quantity": number,
                  "unitPrice": number,
                  "category": "CATEGORY_NAME"
                }
              ]
            }

            Assign each item exactly one category from this list:
            FOOD_PRODUCE  — fresh or frozen fruit, vegetables, herbs, fresh spices, salads
            FOOD_DAIRY    — milk, cheese, yogurt, kefir, cream, butter, ayran, sour cream
            FOOD_PROTEIN  — meat, poultry, fish, seafood, eggs, deli meats, sausages
            FOOD_BAKERY   — bread, rolls, loaves, bagels, crackers, toast (non-sweet)
            FOOD_STAPLES  — pasta, rice, flour, sugar, salt, oil, vinegar, sauces, dry spices, condiments, canned goods, cereals, oats
            FOOD_SNACKS   — chips, crisps, popcorn, chocolate, candy, cookies, cake, ice cream, sweet pastries, desserts, salty snacks
            FOOD_DRINKS   — water, juice, soda, coffee, tea, energy drinks, smoothies (non-alcoholic)
            FOOD_ALCOHOL  — beer, wine, champagne, spirits, cider, any alcoholic drink
            FOOD_OTHER    — food that does not fit any of the above food categories
            MEDICINE      — prescription drugs, over-the-counter medicine, pharmacy items
            SUPPLEMENTS   — vitamins, minerals, protein powder, health supplements
            HYGIENE       — soap, shampoo, toothpaste, deodorant, personal care, cosmetics
            HOUSEHOLD     — kitchen items, home goods, light bulbs, batteries, tools, storage
            CLEANING      — detergent, cleaning spray, mops, sponges, trash bags, fabric softener
            CLOTHING      — clothes, shoes, accessories, textiles, socks, underwear
            ENTERTAINMENT — books, games, movies, sports equipment, hobbies, toys for adults
            ELECTRONICS   — devices, cables, chargers, tech accessories, batteries (for devices)
            PETS          — pet food, pet accessories, veterinary products, litter
            CHILDREN      — baby food, baby products, toys, school supplies, children's clothing
            TRANSPORT     — fuel, parking, public transport tickets, car accessories, toll
            OTHER         — anything that does not fit any of the above categories

            Rules:
            - Use null (not empty string) for any field that cannot be read
            - quantity defaults to 1 if not shown
            - unitPrice is price per single unit; if only the line total is shown, divide by quantity
            - purchaseDate must be YYYY-MM-DD
            - Numbers must not contain currency symbols
            - totalAmount is the final total paid (after all discounts)
            """;

    @Value("${gemini.api-key}")
    private String apiKey;

    @Value("${gemini.base-url}")
    private String baseUrl;

    @Value("${gemini.model}")
    private String model;

    // ObjectMapper is injected — we reuse the Spring-configured instance (Jackson modules, etc.)
    private final ObjectMapper objectMapper;
    private RestClient restClient;

    public GeminiClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // Not final: baseUrl is injected via @Value and is unavailable at constructor time,
    // so RestClient must be built lazily in @PostConstruct.
    @PostConstruct
    private void init() {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    /**
     * Sends the receipt image to Gemini and returns the extracted structured data.
     *
     * @param imageBytes raw bytes of the uploaded receipt image
     * @param mimeType   MIME type of the image (e.g. "image/jpeg", "image/png")
     * @return parsed receipt data — fields may be null if Gemini couldn't read them
     * @throws GeminiParsingException if the API call fails or the response cannot be parsed
     */
    public GeminiReceiptResult parseReceipt(byte[] imageBytes, String mimeType) {
        log.debug("Sending receipt image to Gemini ({} bytes, {})", imageBytes.length, mimeType);

        String base64Image = Base64.getEncoder().encodeToString(imageBytes);
        ObjectNode requestBody = buildRequest(base64Image, mimeType);

        try {
            String rawResponse = restClient.post()
                    .uri("/models/{model}:generateContent?key={key}", model, apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    // Log the full Gemini error body so we can diagnose API rejections
                    .onStatus(status -> !status.is2xxSuccessful(), (req, resp) -> {
                        String errorBody = new String(resp.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        log.error("Gemini API returned HTTP {}: {}", resp.getStatusCode(), errorBody);
                        throw new GeminiParsingException(
                                "Gemini API error " + resp.getStatusCode() + ": " + errorBody, null);
                    })
                    .body(String.class);

            GeminiReceiptResult result = extractResult(rawResponse);
            log.info("Gemini parsed receipt: vendor={}, items={}",
                    result.vendorName(),
                    result.items() != null ? result.items().size() : 0);
            return result;

        } catch (GeminiParsingException e) {
            throw e; // rethrow our own exception as-is
        } catch (Exception e) {
            log.error("Gemini call threw unexpected exception: {}", e.getMessage(), e);
            throw new GeminiParsingException("Gemini API call failed", e);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Builds the Gemini generateContent request body.
     * Uses ObjectMapper nodes directly to avoid null-field serialization issues
     * that would occur with a Java record (Jackson omits nulls only with @JsonInclude).
     */
    private ObjectNode buildRequest(String base64Image, String mimeType) {
        ObjectNode root = objectMapper.createObjectNode();

        ArrayNode contents = root.putArray("contents");
        ObjectNode content = contents.addObject();
        ArrayNode parts = content.putArray("parts");

        // Part 1 — the receipt image as base64 inline data
        ObjectNode imagePart = parts.addObject();
        ObjectNode inlineData = imagePart.putObject("inlineData");
        inlineData.put("mimeType", mimeType);
        inlineData.put("data", base64Image);

        // Part 2 — the extraction prompt
        parts.addObject().put("text", RECEIPT_PROMPT);

        // Force JSON output — Gemini won't wrap the response in markdown code blocks
        ObjectNode generationConfig = root.putObject("generationConfig");
        generationConfig.put("responseMimeType", "application/json");
        // Low temperature = deterministic structured output, less creative guessing
        generationConfig.put("temperature", 0.1);

        return root;
    }

    /**
     * Extracts the JSON text from Gemini's response envelope and deserializes it.
     *
     * Gemini response structure:
     * { "candidates": [{ "content": { "parts": [{ "text": "{...json...}" }] } }] }
     */
    private GeminiReceiptResult extractResult(String rawResponse) {
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            // Use .path() not .get() — path() returns MissingNode instead of null,
            // so the chain never throws NPE when a field is absent.
            String jsonText = root
                    .path("candidates").path(0)
                    .path("content")
                    .path("parts").path(0)
                    .path("text")
                    .asText();

            return objectMapper.readValue(jsonText, GeminiReceiptResult.class);

        } catch (Exception e) {
            log.error("Failed to parse Gemini response: {}", rawResponse);
            throw new GeminiParsingException("Could not parse Gemini response JSON", e);
        }
    }
}
