package com.familyhub.entity.enums;

/**
 * Spending categories assigned by Gemini during receipt parsing.
 * Each value maps directly to the VARCHAR stored in receipt_items.category
 * and budget_limits.category (EnumType.STRING).
 */
public enum SpendingCategory {

    // ── Food ─────────────────────────────────────────────────────────────────
    /** Fresh and frozen fruit, vegetables, herbs, fresh spices */
    FOOD_PRODUCE,
    /** Milk, cheese, yogurt, kefir, cream, butter, ayran */
    FOOD_DAIRY,
    /** Meat, fish, seafood, eggs */
    FOOD_PROTEIN,
    /** Bread, rolls, loaves, crackers (non-sweet) */
    FOOD_BAKERY,
    /** Pasta, rice, flour, sugar, salt, oil, sauces, dry spices, condiments */
    FOOD_STAPLES,
    /** Chips, chocolate, candy, cookies, cakes, ice cream, salty snacks */
    FOOD_SNACKS,
    /** Water, juice, tea, coffee, soda, energy drinks (non-alcoholic) */
    FOOD_DRINKS,
    /** Beer, wine, champagne, spirits, cider and other alcoholic drinks */
    FOOD_ALCOHOL,
    /** Food that does not fit any of the above food categories */
    FOOD_OTHER,

    // ── Health ───────────────────────────────────────────────────────────────
    /** Prescription drugs, over-the-counter medicine, pharmacy items */
    MEDICINE,
    /** Vitamins, minerals, protein powder, health supplements */
    SUPPLEMENTS,

    // ── Home ─────────────────────────────────────────────────────────────────
    /** Soap, shampoo, toothpaste, deodorant, personal care */
    HYGIENE,
    /** Kitchen items, home goods, light bulbs, batteries, tools */
    HOUSEHOLD,
    /** Detergent, cleaning spray, mops, sponges, trash bags */
    CLEANING,

    // ── Lifestyle ────────────────────────────────────────────────────────────
    /** Clothes, shoes, accessories, textiles */
    CLOTHING,
    /** Books, games, movies, sports equipment, hobbies */
    ENTERTAINMENT,
    /** Devices, cables, chargers, tech accessories */
    ELECTRONICS,

    // ── Family ───────────────────────────────────────────────────────────────
    /** Pet food, pet accessories, veterinary products */
    PETS,
    /** Baby food, toys, school supplies, children's clothing */
    CHILDREN,

    // ── Other ────────────────────────────────────────────────────────────────
    /** Fuel, parking, public transport tickets, car accessories */
    TRANSPORT,
    /** Anything that does not fit any of the above categories */
    OTHER
}
