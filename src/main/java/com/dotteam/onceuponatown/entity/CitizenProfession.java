package com.dotteam.onceuponatown.entity;

public enum CitizenProfession {
    /** Food producers **/
    FARMER(ProfessionCategory.FOOD_PRODUCER),
    FISHERMAN(ProfessionCategory.FOOD_PRODUCER),

    /** Resources producers **/
    LUMBERJACK(ProfessionCategory.RESOURCE_PRODUCER),
    MINER(ProfessionCategory.RESOURCE_PRODUCER),

    /** Artisans **/
    CARPENTER(ProfessionCategory.ARTISAN),      // Craft wood blocks
    STONEMASON(ProfessionCategory.ARTISAN),     // Craft stone blocks
    GLASSBLOWER(ProfessionCategory.ARTISAN),    // Craft glass blocks
    SMITH(ProfessionCategory.ARTISAN),          // Craft tools, weapons, armors, and other metal objects
    TEXTILE_WORKER(ProfessionCategory.ARTISAN),
    DYER(ProfessionCategory.ARTISAN),           // French : teinturier

    /** Merchants **/
    INNKEEPER(ProfessionCategory.MERCHANT), // French : aubergiste
    PEDDLER(ProfessionCategory.MERCHANT),   // French : colporteur, marchand ambulant

    /** Military personnel **/
    SOLDIER(ProfessionCategory.MILITARY_PERSONNEL),
    MERCENARY(ProfessionCategory.MILITARY_PERSONNEL),

    /** Religion **/
    PRIEST(ProfessionCategory.RELIGIOUS),
    DRUID(ProfessionCategory.RELIGIOUS),

    /** Artist **/
    BARD(ProfessionCategory.ARTIST),
    PAINTER(ProfessionCategory.ARTIST),
    JEWELER(ProfessionCategory.ARTIST), // French : bijoutier/joaillier

    /** Miscellaneous **/
    LIBRARIAN(ProfessionCategory.MISC),
    FARRIER(ProfessionCategory.MISC), // French : maréchal-ferrant
    BUILDER(ProfessionCategory.MISC),
    EXPLORER(ProfessionCategory.MISC),
    MONSTER_HUNTER(ProfessionCategory.MISC),
    UNEMPLOYED(ProfessionCategory.MISC),
    NITWIT(ProfessionCategory.MISC);

    private ProfessionCategory professionCategory;

    CitizenProfession(ProfessionCategory category){this.professionCategory = category;}

    public byte getId() {
        return (byte) this.ordinal();
    }

    public String toString() {
        return name().toLowerCase();
    }

    public static CitizenProfession byId(byte id) {
        for(CitizenProfession profession : CitizenProfession.values()) {
            if (id == profession.getId()) {
                return profession;
            }
        }
        return UNEMPLOYED;
    }

    private enum ProfessionCategory {
        FOOD_PRODUCER,
        RESOURCE_PRODUCER,
        ARTISAN,
        MERCHANT,
        MILITARY_PERSONNEL,
        RELIGIOUS,
        ARTIST,
        MISC
    }
}
