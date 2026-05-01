package com.familyhub.entity.enums;

/**
 * Spending categories assigned by Gemini during receipt parsing.
 * Each value maps directly to the VARCHAR stored in receipt_items.category (EnumType.STRING).
 * The description field is shown as a tooltip on the spending page.
 */
public enum SpendingCategory {

    // ── Food ─────────────────────────────────────────────────────────────────
    FOOD_PRODUCE    ("Fresh & frozen fruit, vegetables, herbs, fresh spices"),
    FOOD_DAIRY      ("Milk, cheese, yogurt, kefir, cream, butter, sour cream"),
    FOOD_PROTEIN    ("Meat, poultry, fish, seafood, eggs, deli meats, sausages"),
    FOOD_BAKERY     ("Bread, rolls, loaves, bagels, crackers (non-sweet)"),
    FOOD_STAPLES    ("Pasta, rice, oil, sugar, sauces, dry spices, canned goods, cereals"),
    FOOD_SNACKS     ("Chips, chocolate, candy, cookies, ice cream, sweet pastries"),
    FOOD_DRINKS     ("Water, juice, coffee, tea, soda, energy drinks (non-alcoholic)"),
    FOOD_ALCOHOL    ("Beer, wine, champagne, spirits, cider and other alcoholic drinks"),
    FOOD_OTHER      ("Food that does not fit any specific food category"),

    // ── Health ───────────────────────────────────────────────────────────────
    MEDICINE        ("Prescription drugs, over-the-counter medicine, pharmacy items"),
    SUPPLEMENTS     ("Vitamins, minerals, protein powder, health supplements"),

    // ── Home ─────────────────────────────────────────────────────────────────
    HYGIENE         ("Soap, shampoo, toothpaste, deodorant, cosmetics, personal care"),
    HOUSEHOLD       ("Kitchen items, home goods, light bulbs, batteries, tools, storage"),
    CLEANING        ("Detergent, cleaning sprays, mops, sponges, trash bags"),

    // ── Lifestyle ────────────────────────────────────────────────────────────
    CLOTHING        ("Clothes, shoes, accessories, textiles, socks, underwear"),
    ENTERTAINMENT   ("Books, games, movies, sports equipment, hobbies, adult toys"),
    ELECTRONICS     ("Devices, cables, chargers, tech accessories"),

    // ── Family ───────────────────────────────────────────────────────────────
    PETS            ("Pet food, pet accessories, veterinary products, litter"),
    CHILDREN        ("Baby food, baby products, toys, school supplies, children's clothing"),

    // ── Other ────────────────────────────────────────────────────────────────
    TRANSPORT       ("Fuel, parking, public transport tickets, car accessories, toll"),
    OTHER           ("Anything that does not fit any of the above categories");

    private final String description;

    SpendingCategory(String description) {
        this.description = description;
    }

    public String getDescription() { return description; }
}
