package com.rainbow_universe.bettercode.core.place;

public final class DefaultMenuRouteResolver implements MenuRouteResolver {
    @Override
    public String resolvePrimaryMenuKey(PlaceRuntimeEntry entry) {
        if (entry == null) {
            return "";
        }
        String raw = entry.name() == null ? "" : entry.name().trim();
        if (raw.isEmpty()) {
            return "";
        }
        int idx = raw.indexOf("||");
        String base = idx >= 0 ? raw.substring(0, idx).trim() : raw;
        String scope = idx >= 0 ? raw.substring(idx + 2).trim().toLowerCase() : "";
        if (scope.contains("игрок по условию")) {
            return "выбрать игроков по условию";
        }
        if (scope.contains("моб по условию")) {
            return "выбрать мобов по условию";
        }
        if (scope.contains("сущность по условию")) {
            return "выбрать сущности по условию";
        }
        return base.toLowerCase();
    }
}
