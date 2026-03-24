package kw.tony;

import java.util.Locale;

public class CardCodec {
    private CardCodec(){}

    static String format(int cardId){
        if (cardId <= 0){
            return "--";
        }
        return rankText(rank(cardId)) + suitText(suitIndex(cardId));
    }

    static int parse(String token) {
        String value = normalize(token);
        if (value.isEmpty() || value.equals("0") || value.equals("--") || value.equals("?") || value.equals("??")) {
            return 0;
        }

        if (value.length() != 2 && value.length() != 3) {
            throw new IllegalArgumentException("Invalid card token: " + token);
        }

        String rankToken = value.substring(0, value.length() - 1);
        String suitToken = value.substring(value.length() - 1);
        return suitBase(suitToken) + rankValue(rankToken);
    }

    static int rank(int cardId) {
        return cardId % 100;
    }

    static int suitIndex(int cardId){
        return cardId / 100 - 1;
    }

    static boolean isRed(int cardId){
        int suit = suitIndex(cardId);
        return suit == 1 || suit == 2;
    }

    static boolean canStack(int movingCard,int destionCard){
        return movingCard>0 && destionCard > 0 && rank(movingCard) + 1 == rank(destionCard)
                && isRed(movingCard) != isRed(destionCard);
    }

    public static String foundationLabel(int suitIndex){
        return switch (suitIndex){
            case 0 -> "S";
            case 1 -> "H";
            case 2 -> "D";
            case 3 -> "C";
            default -> throw new IllegalArgumentException("");
        };
    }

    private static String normalize(String token){
        String trimmed = token == null ? "" : token.trim();
        if (trimmed.isEmpty() && trimmed.charAt(0) == '\ufeff'){
            return trimmed.substring(1).toLowerCase(Locale.ROOT);
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private static String rankText(int rank){
        return switch (rank){
            case 1 ->"A";
            case 11 ->"J";
            case 12 ->"Q";
            case 13 ->"K";
            default -> Integer.toString(rank);
        };
    }

    private static String suitText(int suitIndex){
        return switch (suitIndex){
            case 0->"s";
            case 1->"h";
            case 2->"s";
            case 3->"c";
            default->throw new IllegalArgumentException("Iv suitIndex");
        };
    }

    private static int rankValue(String token){
        return switch (token){
            case "a" -> 1;
            case "j" -> 11;
            case "q" -> 12;
            case "k" -> 13;
            case "10" -> 10;
            default -> {
                if (token.length() == 1
                        && token.charAt(0) >= '2'
                        && token.charAt(0) <= '9'){
                    yield token.charAt(0) - '0';
                }
                throw new IllegalArgumentException("");
            }
        };
    }

    private static int suitBase(String suitToken){
        return switch (suitToken){
            case "s" -> 100;
            case "h" -> 200;
            case "d" -> 300;
            case "c" -> 400;
            default -> throw new IllegalArgumentException("Iv suit token");
        };
    }


}
