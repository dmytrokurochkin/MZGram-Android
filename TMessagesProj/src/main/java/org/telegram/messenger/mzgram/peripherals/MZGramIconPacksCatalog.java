/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Icon packs catalog with license verification (appearance.icon-packs-catalog).
 * Enforces owner condition: only icon packs with verified open-source / permissive
 * licenses (Apache-2.0, MIT, SIL OFL) are permitted into the active catalog.
 * Packs with unknown or proprietary licenses are excluded.
 */

package org.telegram.messenger.mzgram.peripherals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MZGramIconPacksCatalog {

    public static final String LICENSE_APACHE2 = "Apache 2.0";
    public static final String LICENSE_MIT = "MIT";
    public static final String LICENSE_UNKNOWN = "Unknown (Excluded)";

    public static final class CatalogItem {
        public final String id;
        public final String name;
        public final String version;
        public final String format;
        public final String license;
        public final int iconsCount;
        public final boolean approved;

        public CatalogItem(String id, String name, String version, String format, String license, int iconsCount, boolean approved) {
            this.id = id;
            this.name = name;
            this.version = version;
            this.format = format;
            this.license = license;
            this.iconsCount = iconsCount;
            this.approved = approved;
        }
    }

    private static final List<CatalogItem> allItems = new ArrayList<>();
    private static final List<CatalogItem> approvedItems = new ArrayList<>();

    static {
        // Verified Permissive Open-Source Packs
        addPack(new CatalogItem("custom.materialdesign3-by-mihailkotovski", "Material Design 3", "3.0.0", "SVG", LICENSE_APACHE2, 100, true));
        addPack(new CatalogItem("custom.fe5fc9d5-c76f-44a7-a152-bbddae5bfb75", "Material Symbols", "1.7", "SVG", LICENSE_APACHE2, 955, true));
        addPack(new CatalogItem("materialexpressiveicons", "Rounded Material Symbols", "1.4", "SVG", LICENSE_APACHE2, 733, true));
        addPack(new CatalogItem("custom.60052a46-ac77-4247-a31d-f87edb387825", "Phosphor Icons", "1.0", "SVG", LICENSE_MIT, 853, true));
        addPack(new CatalogItem("custom.449c98ae-bcae-4fde-8d5a-a55f587a9bd6", "PhosphorPack", "1.2", "SVG", LICENSE_MIT, 254, true));
        addPack(new CatalogItem("remix_new", "Remix New", "1.4", "SVG", LICENSE_APACHE2, 28, true));
        addPack(new CatalogItem("custom.b5b19e39-a804-417d-91d6-96cae2d8b2e9", "Fluent 2 Design Filled", "2.1", "SVG/PNG", LICENSE_MIT, 1290, true));

        // Packs with Unknown / Unverified Licenses (Excluded from active list by policy)
        addPack(new CatalogItem("custom.40af1cd4-a1f8-4e49-8c98-404034707588", "DottedPack", "1.0", "PNG", LICENSE_UNKNOWN, 145, false));
        addPack(new CatalogItem("custom.7355f311-821c-4319-b3c9-d1143fe2d6ee", "GalaxyIcons", "1.0", "PNG", LICENSE_UNKNOWN, 344, false));
        addPack(new CatalogItem("custom.98892f5e-34b0-4a28-85e9-5085fc90cf22", "GovnPack", "1.4", "PNG", LICENSE_UNKNOWN, 530, false));
        addPack(new CatalogItem("custom.2aea3e82-3e8d-46ab-9e6b-8b2e1fb80ca8", "Liquid Glass Pack", "1.0", "SVG/PNG", LICENSE_UNKNOWN, 1000, false));
        addPack(new CatalogItem("custom.f98e3580-37b3-4078-b1f4-3db27c697a40", "NOTHING", "1.10", "PNG", LICENSE_UNKNOWN, 605, false));
        addPack(new CatalogItem("custom.f5840dfa-104b-4a82-9a98-bae96a45ddf1", "Pixel icons", "1.6", "PNG", LICENSE_UNKNOWN, 1237, false));
        addPack(new CatalogItem("custom.807a3a58-14ef-42b7-87b4-bb8a93060b61", "PlumpyPack", "1.0.2", "PNG", LICENSE_UNKNOWN, 1239, false));
        addPack(new CatalogItem("custom.e3207e38-a16b-4e6b-a4bf-af1b8b4d33cc", "PuffyPack", "1.1", "PNG", LICENSE_UNKNOWN, 261, false));
        addPack(new CatalogItem("custom.db927f52-272b-4df3-abdd-99385e0050af", "UPixel", "1.5", "PNG", LICENSE_UNKNOWN, 532, false));
    }

    private static void addPack(CatalogItem item) {
        allItems.add(item);
        if (item.approved) {
            approvedItems.add(item);
        }
    }

    private MZGramIconPacksCatalog() {
    }

    public static List<CatalogItem> getApprovedPacks() {
        return Collections.unmodifiableList(approvedItems);
    }

    public static List<CatalogItem> getAllPacks() {
        return Collections.unmodifiableList(allItems);
    }

    public static int getApprovedCount() {
        return approvedItems.size();
    }

    public static int getExcludedCount() {
        return allItems.size() - approvedItems.size();
    }
}
