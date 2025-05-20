package de.dhbw.mh.redeggs;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static de.dhbw.mh.redeggs.CodePointRange.single;

public class AlternativeRegexParser {

    private final SymbolFactory factory;
    private String pattern;
    private int pos;

    public AlternativeRegexParser(SymbolFactory factory) {
        this.factory = factory;
    }

    public RegularEggspression analyze(String regex) throws RedeggsParseException {
        this.pattern = regex;
        this.pos = 0;
        return buildUnion();
    }

    private RegularEggspression buildUnion() throws RedeggsParseException {
        RegularEggspression left = buildConcat();
        while (current('|')) {
            consume();
            RegularEggspression right = buildConcat();
            left = new RegularEggspression.Alternation(left, right);
        }
        return left;
    }

    private RegularEggspression buildConcat() throws RedeggsParseException {
        RegularEggspression left = buildRepetition();
        while (hasMore() && (isLiteral(peek()) || peek() == '[' || peek() == '(')) {
            RegularEggspression right = buildRepetition();
            left = new RegularEggspression.Concatenation(left, right);
        }
        return left;
    }

    private RegularEggspression buildRepetition() throws RedeggsParseException {
        RegularEggspression base = resolveAtom();
        if (current('*')) {
            consume();
            return new RegularEggspression.Star(base);
        }
        return base;
    }

    private RegularEggspression resolveAtom() throws RedeggsParseException {
        if (!hasMore()) throw new RedeggsParseException("Unexpected end", pos);

        char ch = peek();

        if (isLiteral(ch)) {
            return new RegularEggspression.Literal(factory.newSymbol().include(single(consume())).andNothingElse());
        }

        if (ch == '(') {
            consume();
            RegularEggspression inner = buildUnion();
            expect(')');
            return inner;
        }

        if (ch == '[') {
            consume();
            boolean negate = current('^');
            if (negate) consume();

            List<CodePointRange> ranges = new ArrayList<>();
            while (!current(']')) {
                char start = expectLiteral();
                if (current('-')) {
                    consume();
                    char end = expectLiteral();
                    ranges.add(CodePointRange.range(start, end));
                } else {
                    ranges.add(single(start));
                }
            }
            consume(); // consume ']'

            var symbolBuilder = factory.newSymbol();
            if (negate) symbolBuilder.exclude(ranges.toArray(new CodePointRange[0]));
            else symbolBuilder.include(ranges.toArray(new CodePointRange[0]));

            return new RegularEggspression.Literal(symbolBuilder.andNothingElse());
        }

        if (ch == 'ε') {
            consume();
            return new RegularEggspression.EmptyWord();
        }

        if (ch == '∅') {
            consume();
            return new RegularEggspression.EmptySet();
        }

        throw new RedeggsParseException("Unknown character: " + ch, pos);
    }

    // Utils

    private boolean hasMore() {
        return pos < pattern.length();
    }

    private char peek() {
        return pattern.charAt(pos);
    }

    private boolean current(char expected) {
        return hasMore() && peek() == expected;
    }

    private char consume() {
        return pattern.charAt(pos++);
    }

    private void expect(char expected) throws RedeggsParseException {
        if (!current(expected)) {
            throw new RedeggsParseException("Expected '" + expected + "'", pos);
        }
        consume();
    }

    private boolean isLiteral(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private char expectLiteral() throws RedeggsParseException {
        if (!hasMore() || !isLiteral(peek())) {
            throw new RedeggsParseException("Expected literal", pos);
        }
        return consume();
    }
}
