package com.familyhub.entity.enums;

// Event category — shown as an icon + label in the event form and calendar.
// icon: FontAwesome class used in templates via ${type.icon}
// label: human-readable display name used in the form selector
public enum EventType {

    BIRTHDAY ("fa-solid fa-cake-candles",    "Birthday"),
    PARTY    ("fa-solid fa-champagne-glasses","Party"),
    MEDICAL  ("fa-solid fa-stethoscope",     "Medical"),
    SCHOOL   ("fa-solid fa-graduation-cap",  "School"),
    SPORT    ("fa-solid fa-futbol",          "Sport"),
    TRAVEL   ("fa-solid fa-plane",           "Travel"),
    FAMILY   ("fa-solid fa-people-roof",     "Family"),
    WORK     ("fa-solid fa-briefcase",       "Work"),
    HOLIDAY  ("fa-solid fa-umbrella-beach",  "Holiday"),
    SHOPPING ("fa-solid fa-cart-shopping",   "Shopping"),
    PET      ("fa-solid fa-paw",             "Pet"),
    OTHER    ("fa-solid fa-calendar-day",    "Other");

    private final String icon;
    private final String label;

    EventType(String icon, String label) {
        this.icon  = icon;
        this.label = label;
    }

    // Getters used by Thymeleaf: ${type.icon}, ${type.label}
    public String getIcon()  { return icon;  }
    public String getLabel() { return label; }
}
