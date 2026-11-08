package org.telegram.ui.mzgram;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

import org.telegram.messenger.mzgram.MZGramConfig;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * MZGram: inline calculator in the message field. Typing "=" after an
 * arithmetic expression (25*40=) appends the result (25*40=1000). Works only
 * offline: no currency rates. Idea from Telegram clients with an inline
 * calculator (InlineMathController); this is MZGram's own small implementation.
 */
public class MZGramInlineMath {

    private static final int MAX_EXPRESSION = 120;

    /** Adds the calculator to a message field; it does nothing while the switch is off. */
    public static void attach(EditText field) {
        if (field == null) {
            return;
        }
        field.addTextChangedListener(new TextWatcher() {
            private boolean typedEquals;
            private boolean busy;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                typedEquals = MZGramConfig.inlineMath && !busy && before == 0 && count == 1
                    && start == s.length() - 1 && s.charAt(start) == '=';
            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (!typedEquals || busy) {
                    return;
                }
                typedEquals = false;
                String result = resultFor(editable.subSequence(0, editable.length() - 1).toString());
                if (result == null) {
                    return;
                }
                busy = true;
                try {
                    editable.append(result);
                } finally {
                    busy = false;
                }
            }
        });
    }

    /** The result of the expression that ends the text, or null when there is none. */
    public static String resultFor(String text) {
        int end = text.length();
        int start = end;
        while (start > 0 && isExpressionChar(text.charAt(start - 1))) {
            start--;
        }
        String expression = text.substring(start, end).trim();
        if (expression.isEmpty() || expression.length() > MAX_EXPRESSION) {
            return null;
        }
        boolean hasDigit = false;
        boolean hasOperator = false;
        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            if (c >= '0' && c <= '9') {
                hasDigit = true;
            } else if ("+-*/×÷^%".indexOf(c) >= 0) {
                hasOperator = true;
            }
        }
        if (!hasDigit || !hasOperator) {
            return null;
        }
        try {
            double value = new Parser(expression).parse();
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                return null;
            }
            return format(value);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean isExpressionChar(char c) {
        return (c >= '0' && c <= '9') || c == ' ' || c == '.' || c == ',' || "+-*/×÷^%()".indexOf(c) >= 0;
    }

    static String format(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1e15) {
            return Long.toString((long) value);
        }
        BigDecimal decimal = new BigDecimal(value).round(new MathContext(12)).stripTrailingZeros();
        return decimal.toPlainString();
    }

    /** Recursive descent: + - over * / over ^ over unary minus, with % after a number. */
    private static class Parser {
        private final String text;
        private int pos;

        Parser(String text) {
            this.text = text;
        }

        double parse() {
            double value = sum();
            skipSpaces();
            if (pos != text.length()) {
                throw new IllegalArgumentException("unexpected " + text.charAt(pos));
            }
            return value;
        }

        private void skipSpaces() {
            while (pos < text.length() && text.charAt(pos) == ' ') {
                pos++;
            }
        }

        private boolean take(char c) {
            skipSpaces();
            if (pos < text.length() && text.charAt(pos) == c) {
                pos++;
                return true;
            }
            return false;
        }

        private double sum() {
            double value = product();
            while (true) {
                if (take('+')) {
                    value += product();
                } else if (take('-')) {
                    value -= product();
                } else {
                    return value;
                }
            }
        }

        private double product() {
            double value = power();
            while (true) {
                if (take('*') || take('×')) {
                    value *= power();
                } else if (take('/') || take('÷')) {
                    double divisor = power();
                    if (divisor == 0) {
                        throw new ArithmeticException("division by zero");
                    }
                    value /= divisor;
                } else {
                    return value;
                }
            }
        }

        private double power() {
            double base = unary();
            if (take('^')) {
                return Math.pow(base, power());
            }
            return base;
        }

        private double unary() {
            if (take('-')) {
                return -unary();
            }
            if (take('+')) {
                return unary();
            }
            return postfix();
        }

        private double postfix() {
            double value = primary();
            while (take('%')) {
                value /= 100;
            }
            return value;
        }

        private double primary() {
            if (take('(')) {
                double value = sum();
                if (!take(')')) {
                    throw new IllegalArgumentException("missing )");
                }
                return value;
            }
            skipSpaces();
            int start = pos;
            while (pos < text.length() && (Character.isDigit(text.charAt(pos)) || text.charAt(pos) == '.' || text.charAt(pos) == ',')) {
                pos++;
            }
            if (start == pos) {
                throw new IllegalArgumentException("number expected");
            }
            return Double.parseDouble(text.substring(start, pos).replace(',', '.'));
        }
    }
}
