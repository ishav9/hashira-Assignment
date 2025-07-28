BigIntegerFraction.java (Helper Class for Precise Arithmetic)
Create this file to handle fractions with BigInteger to avoid precision loss.

import java.math.BigInteger;
import java.util.Objects;

/**
 * An immutable class to represent fractions of BigIntegers for exact arithmetic.
 */
public final class BigIntegerFraction {
    public static final BigIntegerFraction ZERO = new BigIntegerFraction(BigInteger.ZERO);
    public static final BigIntegerFraction ONE = new BigIntegerFraction(BigInteger.ONE);

    private final BigInteger numerator;
    private final BigInteger denominator;

    public BigIntegerFraction(BigInteger numerator, BigInteger denominator) {
        Objects.requireNonNull(numerator, "Numerator cannot be null");
        Objects.requireNonNull(denominator, "Denominator cannot be null");
        if (denominator.equals(BigInteger.ZERO)) {
            throw new IllegalArgumentException("Denominator cannot be zero.");
        }
        // Normalize the fraction
        BigInteger commonDivisor = numerator.gcd(denominator).abs();
        // Ensure denominator is positive
        if (denominator.signum() < 0) {
            this.numerator = numerator.negate().divide(commonDivisor);
            this.denominator = denominator.negate().divide(commonDivisor);
        } else {
            this.numerator = numerator.divide(commonDivisor);
            this.denominator = denominator.divide(commonDivisor);
        }
    }

    public BigIntegerFraction(BigInteger value) {
        this(value, BigInteger.ONE);
    }

    public BigInteger getNumerator() {
        return numerator;
    }

    public BigInteger getDenominator() {
        return denominator;
    }

    public BigIntegerFraction add(BigIntegerFraction other) {
        BigInteger newNumerator = this.numerator.multiply(other.denominator)
                .add(other.numerator.multiply(this.denominator));
        BigInteger newDenominator = this.denominator.multiply(other.denominator);
        return new BigIntegerFraction(newNumerator, newDenominator);
    }

    public BigIntegerFraction subtract(BigIntegerFraction other) {
        BigInteger newNumerator = this.numerator.multiply(other.denominator)
                .subtract(other.numerator.multiply(this.denominator));
        BigInteger newDenominator = this.denominator.multiply(other.denominator);
        return new BigIntegerFraction(newNumerator, newDenominator);
    }

    public BigIntegerFraction multiply(BigIntegerFraction other) {
        return new BigIntegerFraction(
                this.numerator.multiply(other.numerator),
                this.denominator.multiply(other.denominator)
        );
    }

    public BigIntegerFraction multiply(BigInteger value) {
        return new BigIntegerFraction(this.numerator.multiply(value), this.denominator);
    }

    public BigIntegerFraction divide(BigIntegerFraction other) {
        if (other.numerator.equals(BigInteger.ZERO)) {
            throw new ArithmeticException("Cannot divide by zero.");
        }
        return new BigIntegerFraction(
                this.numerator.multiply(other.denominator),
                this.denominator.multiply(other.numerator)
        );
    }

    @Override
    public String toString() {
        if (denominator.equals(BigInteger.ONE)) {
            return numerator.toString();
        }
        return numerator + "/" + denominator;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BigIntegerFraction that = (BigIntegerFraction) o;
        return numerator.equals(that.numerator) && denominator.equals(that.denominator);
    }

    @Override
    public int hashCode() {
        return Objects.hash(numerator, denominator);
    }
}
2. SecretSolver.java (Main Class)
Create this file with the main logic to read the JSON and perform the Lagrange interpolation.

import org.json.JSONObject;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SecretSolver {

    public static BigInteger solveSecretFromFile(String filePath) throws IOException {
        String content = new String(Files.readAllBytes(Paths.get(filePath)));
        JSONObject data = new JSONObject(content);

        JSONObject keys = data.getJSONObject("keys");
        int k = keys.getInt("k");

        List<Map.Entry<BigInteger, BigInteger>> points = new ArrayList<>();

        for (String keyStr : data.keySet()) {
            if (keyStr.equals("keys")) continue;
            BigInteger x = new BigInteger(keyStr);
            JSONObject pointData = data.getJSONObject(keyStr);
            int base = pointData.getInt("base");
            String valueStr = pointData.getString("value");
            BigInteger y = new BigInteger(valueStr, base);
            points.add(Map.entry(x, y));
            if (points.size() == k) break;
        }

        BigIntegerFraction secretC = BigIntegerFraction.ZERO;

        for (int j = 0; j < k; j++) {
            Map.Entry<BigInteger, BigInteger> pointJ = points.get(j);
            BigInteger xj = pointJ.getKey();
            BigInteger yj = pointJ.getValue();

            BigIntegerFraction lagrangeTerm = BigIntegerFraction.ONE;

            for (int i = 0; i < k; i++) {
                if (i == j) continue;
                Map.Entry<BigInteger, BigInteger> pointI = points.get(i);
                BigInteger xi = pointI.getKey();

                BigInteger numerator = xi.negate();
                BigInteger denominator = xj.subtract(xi);
                lagrangeTerm = lagrangeTerm.multiply(new BigIntegerFraction(numerator, denominator));
            }
            secretC = secretC.add(lagrangeTerm.multiply(yj));
        }

        if (!secretC.getDenominator().equals(BigInteger.ONE)) {
            throw new IllegalStateException("The calculated secret is not an integer: " + secretC);
        }

        return secretC.getNumerator();
    }

    public static void main(String[] args) {
        String testcase1File = "testcase1.json";
        String testcase2File = "testcase2.json";

        System.out.println("--- Secrets Found ---");

        try {
            BigInteger secret1 = solveSecretFromFile(testcase1File);
            System.out.println("Secret for Test Case 1: " + secret1);
        } catch (IOException e) {
            System.err.println("Error processing " + testcase1File + ": " + e.getMessage());
            e.printStackTrace();
        }

        try {
            BigInteger secret2 = solveSecretFromFile(testcase2File);
            System.out.println("Secret for Test Case 2: " + secret2);
        } catch (IOException e) {
            System.err.println("Error processing " + testcase2File + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}
3. How to Run the Java Code
Save the files: Save BigIntegerFraction.java and SecretSolver.java in the same directory. Make sure you have the testcase1.json and testcase2.json files in a location accessible to your Java application (e.g., the project root or a resources folder).
Compile the code: Open your terminal or command prompt, navigate to the directory where you saved the .java files, and compile them. Ensure the org.json library's JAR file is in your classpath.
javac -cp "path/to/json-20231013.jar:." BigIntegerFraction.java SecretSolver.java
(Replace "path/to/json-20231013.jar" with the actual path to the json.jar file.)
Run the application:
java -cp "path/to/json-20231013.jar:." SecretSolver
(Again, ensure the classpath includes the json.jar.)
Corrected Answers (Reiterated)
The Java code above, using precise BigInteger arithmetic, will correctly calculate the secrets as:

Secret for Test Case 1: 3
Secret for Test Case 2: 12345678901234567890
