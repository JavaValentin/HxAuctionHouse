package com.yourname.hxauctionhouse.utils;

import java.text.DecimalFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PriceUtil {
    private static final Pattern PRICE_PATTERN = Pattern.compile("^(\\d+(?:\\.\\d+)?)(k|m|b)?$", Pattern.CASE_INSENSITIVE);
    private static final DecimalFormat FORMAT = new DecimalFormat("#,##0.##");

    public static double parsePrice(String input) {
        if (input == null || input.isEmpty()) return -1;
        
        input = input.trim().toLowerCase().replaceAll(",", "");
        Matcher matcher = PRICE_PATTERN.matcher(input);
        
        if (!matcher.matches()) return -1;
        
        try {
            double number = Double.parseDouble(matcher.group(1));
            String suffix = matcher.group(2);
            
            if (suffix != null) {
                switch (suffix.toLowerCase()) {
                    case "k" -> number *= 1_000;
                    case "m" -> number *= 1_000_000;
                    case "b" -> number *= 1_000_000_000;
                }
            }
            
            return number;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static String formatPrice(double price) {
        if (price >= 1_000_000_000) {
            return FORMAT.format(price / 1_000_000_000) + "B";
        } else if (price >= 1_000_000) {
            return FORMAT.format(price / 1_000_000) + "M";
        } else if (price >= 1_000) {
            return FORMAT.format(price / 1_000) + "K";
        } else {
            return FORMAT.format(price);
        }
    }
}
