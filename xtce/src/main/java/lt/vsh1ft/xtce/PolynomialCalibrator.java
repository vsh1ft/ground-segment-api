package lt.vsh1ft.xtce;

import java.util.List;

public record PolynomialCalibrator(List<Term> terms) {

    public static final PolynomialCalibrator IDENTITY = new PolynomialCalibrator(List.of(new Term(1, 1.0)));

    public PolynomialCalibrator {
        terms = List.copyOf(terms);
    }

    double apply(long raw) {
        return terms.stream().mapToDouble(term -> term.coefficient() * Math.pow(raw, term.exponent())).sum();
    }

    public record Term(int exponent, double coefficient) {
    }
}
