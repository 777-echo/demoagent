package com.example.tool;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Safe calculator tool that evaluates arithmetic expressions.
 *
 * Supports: +, -, *, /, %, ^ (power), parentheses, and decimals.
 * Does NOT use ScriptEngine (for security); uses a simple recursive-descent parser.
 */
public class CalculatorTool implements Tool {

    private static final Pattern SAFE_EXPR = Pattern.compile("[0-9+\\-*/%^.()\\s]+");

    @Override
    public String name() { return "calculator"; }

    @Override
    public String description() {
        return "Evaluate a mathematical expression. Supports +, -, *, /, %, ^ (power), parentheses, and decimals. Example: \"(2 + 3) * 4 ^ 2\"";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "expression", Map.of(
                    "type", "string",
                    "description", "The mathematical expression to evaluate, e.g. '2+3*4'"
                )
            ),
            "required", java.util.List.of("expression")
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> args, Map<String, Object> sessionState) {
        String expression = (String) args.get("expression");
        if (expression == null || expression.isBlank()) {
            return ToolResult.error("Missing 'expression' argument");
        }

        // Security: only allow safe characters
        if (!SAFE_EXPR.matcher(expression).matches()) {
            return ToolResult.error("Expression contains invalid characters. Only digits, + - * / % ^ . ( ) and spaces are allowed.");
        }

        try {
            double result = evaluate(expression);
            // Format: remove trailing zeros
            String formatted;
            if (result == Math.floor(result) && !Double.isInfinite(result)) {
                formatted = String.valueOf((long) result);
            } else {
                formatted = String.valueOf(result);
            }
            return ToolResult.success(expression + " = " + formatted);
        } catch (Exception e) {
            return ToolResult.error("Failed to evaluate expression: " + e.getMessage());
        }
    }

    // --- Simple recursive-descent parser ---

    private double evaluate(String expression) {
        // We create a new instance for the recursive parse
        return new Parser(expression).parseExpression();
    }

    private static class Parser {
        private final String expr;
        private int pos;

        Parser(String expr) {
            this.expr = expr.replaceAll("\\s+", ""); // remove all whitespace
            this.pos = 0;
        }

        double parseExpression() {
            double result = parseTerm();
            while (pos < expr.length()) {
                char op = expr.charAt(pos);
                if (op == '+' || op == '-') {
                    pos++;
                    double term = parseTerm();
                    if (op == '+') result += term;
                    else result -= term;
                } else {
                    break;
                }
            }
            return result;
        }

        double parseTerm() {
            double result = parsePower();
            while (pos < expr.length()) {
                char op = expr.charAt(pos);
                if (op == '*' || op == '/' || op == '%') {
                    pos++;
                    double factor = parsePower();
                    if (op == '*') result *= factor;
                    else if (op == '/') {
                        if (factor == 0) throw new ArithmeticException("Division by zero");
                        result /= factor;
                    } else {
                        result %= factor;
                    }
                } else {
                    break;
                }
            }
            return result;
        }

        double parsePower() {
            double result = parseUnary();
            while (pos < expr.length() && expr.charAt(pos) == '^') {
                pos++;
                double exponent = parseUnary();
                result = Math.pow(result, exponent);
            }
            return result;
        }

        double parseUnary() {
            if (pos < expr.length() && expr.charAt(pos) == '-') {
                pos++;
                return -parseAtom();
            }
            if (pos < expr.length() && expr.charAt(pos) == '+') {
                pos++;
            }
            return parseAtom();
        }

        double parseAtom() {
            if (pos >= expr.length()) {
                throw new IllegalArgumentException("Unexpected end of expression");
            }

            char ch = expr.charAt(pos);

            // Parenthesized sub-expression
            if (ch == '(') {
                pos++; // skip '('
                double result = parseExpression();
                if (pos >= expr.length() || expr.charAt(pos) != ')') {
                    throw new IllegalArgumentException("Missing closing parenthesis");
                }
                pos++; // skip ')'
                return result;
            }

            // Number literal
            if (Character.isDigit(ch) || ch == '.') {
                int start = pos;
                while (pos < expr.length() && (Character.isDigit(expr.charAt(pos)) || expr.charAt(pos) == '.')) {
                    pos++;
                }
                return Double.parseDouble(expr.substring(start, pos));
            }

            throw new IllegalArgumentException("Unexpected character: '" + ch + "' at position " + pos);
        }
    }
}
