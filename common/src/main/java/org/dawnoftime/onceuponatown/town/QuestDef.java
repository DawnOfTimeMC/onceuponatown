package org.dawnoftime.onceuponatown.town;

import java.util.List;

public record QuestDef(
    String id,
    String type,
    String titleKey,
    String descKey,
    List<ConditionTemplate> conditions,
    RewardTemplate reward,
    long refreshIntervalTicks,
    Prerequisites prerequisites
) {
    public record ConditionTemplate(String type, String item, int required, boolean sendToStock) {}
    public record RewardTemplate(String type, String item, int amount) {}

    public record Prerequisites(
        int minEra,
        int maxEra,
        List<String> requiredBuildings,
        int minResidents,
        int maxResidents,
        List<String> requiredOrientations,
        List<StockCondition> stockConditions
    ) {
        public static final Prerequisites NONE = new Prerequisites(0, Integer.MAX_VALUE, List.of(), 0, Integer.MAX_VALUE, List.of(), List.of());
    }

    public record StockCondition(String item, int min, int max) {}
}
