package com.comficonf.demo.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Runtime configuration of the voucher plugin: settings and vouchers in file order. Immutable. */
public final class VoucherConfig {

    private final VoucherSettings settings;
    private final List<Voucher> vouchers;
    private final Map<String, Voucher> byId;

    public VoucherConfig(VoucherSettings settings, List<Voucher> vouchers) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.vouchers = List.copyOf(vouchers);
        Map<String, Voucher> index = new LinkedHashMap<>();
        for (Voucher voucher : this.vouchers) {
            if (index.put(voucher.id(), voucher) != null) {
                throw new IllegalArgumentException("Duplicate voucher id " + voucher.id());
            }
        }
        this.byId = Map.copyOf(index);
    }

    public VoucherSettings settings() {
        return settings;
    }

    /** Vouchers in the order of the configuration file. */
    public List<Voucher> vouchers() {
        return vouchers;
    }

    public Optional<Voucher> voucher(String id) {
        return Optional.ofNullable(byId.get(id));
    }
}
