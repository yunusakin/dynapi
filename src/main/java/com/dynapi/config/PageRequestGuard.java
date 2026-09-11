package com.dynapi.config;

public final class PageRequestGuard {
    private PageRequestGuard() {
    }

    public static int resolvePage(Integer page, int defaultPage) {
        if (page == null) {
            return defaultPage;
        }
        if (page < 0) {
            throw new IllegalArgumentException("Page must be >= 0");
        }
        return page;
    }

    public static int resolveSize(Integer size, int defaultSize, QueryGuardrailProperties guardrailProperties) {
        if (size == null) {
            return defaultSize;
        }
        if (size <= 0) {
            throw new IllegalArgumentException("Size must be > 0");
        }
        if (size > guardrailProperties.getMaxPageSize()) {
            throw new IllegalArgumentException(
                    "Size exceeds max page size: " + guardrailProperties.getMaxPageSize());
        }
        return size;
    }
}
