package com.xrdxrf.app;

final class CategoryFilter {

    static final String[] CATEGORIES = {
            "All Categories",
            "Minerals",
            "Organic Compounds",
            "Inorganic Compounds",
            "Metals/Elements",
            "Other"
    };

    private static final String NAME_EXPR = "LOWER(COALESCE(c2mineralname,'') || ' ' || COALESCE(c1compoundname,''))";
    private static final String FORMULA_EXPR = "LOWER(COALESCE(c0chemicalformula,''))";

    private static final String MINERALS = "(c2mineralname IS NOT NULL AND c2mineralname != '')";
    private static final String ORGANIC = "(" + FORMULA_EXPR + " LIKE '%c%' AND " + FORMULA_EXPR + " LIKE '%h%')";
    private static final String INORGANIC = "((c2mineralname IS NULL OR c2mineralname = '') AND " + FORMULA_EXPR + " != '' AND (" + FORMULA_EXPR + " NOT LIKE '%c%' OR " + FORMULA_EXPR + " NOT LIKE '%h%'))";

    private static final String METALS = "(" + NAME_EXPR + " LIKE '%iron%'" +
            " OR " + NAME_EXPR + " LIKE '%copper%'" +
            " OR " + NAME_EXPR + " LIKE '%gold%'" +
            " OR " + NAME_EXPR + " LIKE '%silver%'" +
            " OR " + NAME_EXPR + " LIKE '%aluminum%'" +
            " OR " + NAME_EXPR + " LIKE '%aluminium%'" +
            " OR " + NAME_EXPR + " LIKE '%zinc%'" +
            " OR " + NAME_EXPR + " LIKE '%nickel%'" +
            " OR " + NAME_EXPR + " LIKE '%lead%'" +
            " OR " + NAME_EXPR + " LIKE '%tin%'" +
            " OR " + NAME_EXPR + " LIKE '%uranium%'" +
            " OR " + NAME_EXPR + " LIKE '%platinum%'" +
            " OR " + NAME_EXPR + " LIKE '%mercury%'" +
            " OR " + NAME_EXPR + " LIKE '%chromium%'" +
            " OR " + NAME_EXPR + " LIKE '%manganese%'" +
            " OR " + NAME_EXPR + " LIKE '%cobalt%'" +
            " OR " + NAME_EXPR + " LIKE '%molybdenum%'" +
            " OR " + NAME_EXPR + " LIKE '%titanium%'" +
            " OR " + NAME_EXPR + " LIKE '%sodium%'" +
            " OR " + NAME_EXPR + " LIKE '%potassium%'" +
            " OR " + NAME_EXPR + " LIKE '%calcium%'" +
            " OR " + NAME_EXPR + " LIKE '%magnesium%'" +
            " OR " + NAME_EXPR + " LIKE '%element%')";

    static String whereClause(String category) {
        if ("Minerals".equals(category)) {
            return MINERALS;
        }
        if ("Organic Compounds".equals(category)) {
            return ORGANIC;
        }
        if ("Inorganic Compounds".equals(category)) {
            return INORGANIC;
        }
        if ("Metals/Elements".equals(category)) {
            return METALS;
        }
        if ("Other".equals(category)) {
            return "((c2mineralname IS NULL OR c2mineralname = '') AND NOT (" + ORGANIC + " OR " + INORGANIC + " OR " + METALS + "))";
        }
        return "1=1";
    }

    private CategoryFilter() {
    }
}
