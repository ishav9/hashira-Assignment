import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ShamirShelling {

    // --- Finite Field Arithmetic ---
    // We'll use a prime modulus for simplicity.
    // Choose a prime P such that P > all possible values of x, y, and the secret.
    private static final long PRIME_MODULUS = 1000000007L; // A large prime

    // Modular exponentiation (base^exp mod modulus)
    private static long power(long base, long exp) {
        long res = 1;
        base %= PRIME_MODULUS;
        while (exp > 0) {
            if (exp % 2 == 1) res = (res * base) % PRIME_MODULUS;
            base = (base * base) % PRIME_MODULUS;
            exp /= 2;
        }
        return res;
    }

    // Modular inverse (n^-1 mod modulus) using Fermat's Little Theorem (modulus must be prime)
    private static long modInverse(long n) {
        return power(n, PRIME_MODULUS - 2);
    }

    // --- Polynomial Operations ---
    public static class Polynomial {
        private final List<Long> coefficients; // coefficients[i] is coeff of x^i

        // Constructor for a polynomial of degree k-1 (k coefficients)
        public Polynomial(int k) {
            coefficients = new ArrayList<>(Collections.nCopies(k, 0L));
        }

        // Evaluate the polynomial at a given x in the finite field
        public long evaluate(long x) {
            long result = 0;
            long x_power = 1;
            for (long coeff : coefficients) {
                result = (result + (coeff * x_power) % PRIME_MODULUS) % PRIME_MODULUS;
                x_power = (x_power * x) % PRIME_MODULUS;
            }
            return result;
        }

        public List<Long> getCoefficients() {
            return coefficients;
        }

        public void setCoefficient(int index, long value) {
            if (index >= 0 && index < coefficients.size()) {
                coefficients.set(index, value);
            }
        }

        public int getDegree() {
            return coefficients.size() - 1;
        }
    }

    // --- Share Representation ---
    public static class Share {
        long x;
        long y;
        int id; // For debugging or identification

        public Share(long x, long y, int id) {
            this.x = x;
            this.y = y;
            this.id = id;
        }

        @Override
        public String toString() {
            return "Share{id=" + id + ", x=" + x + ", y=" + y + '}';
        }
    }

    // --- Lagrange Interpolation ---

    // Calculates the j-th Lagrange basis polynomial L_j(x) for a set of k points
    // Specifically, we need L_j(0) for secret reconstruction
    // L_j(0) = product_{i=1, i!=j to k} (-x_i) / (x_j - x_i)
    private static long lagrangeBasisAtZero(List<Share> points, int j) {
        long num = 1;
        long den = 1;
        int k = points.size();

        Share shareJ = points.get(j);

        for (int i = 0; i < k; ++i) {
            if (i == j) continue;

            Share shareI = points.get(i);

            // Numerator term: (-x_i)
            long negXi = (PRIME_MODULUS - shareI.x) % PRIME_MODULUS;
            num = (num * negXi) % PRIME_MODULUS;

            // Denominator term: (x_j - x_i)
            long xjMinusXi = (shareJ.x - shareI.x + PRIME_MODULUS) % PRIME_MODULUS;
            den = (den * xjMinusXi) % PRIME_MODULUS;
        }

        return (num * modInverse(den)) % PRIME_MODULUS;
    }

    // Reconstructs the secret (P(0)) using k shares
    // Assumes all input points are valid and from the same polynomial
    public static long reconstructSecret(List<Share> shares) {
        if (shares == null || shares.isEmpty()) {
            return -1; // Error: No shares provided
        }
        if (shares.size() == 1) {
            return shares.get(0).y; // Trivial case if k=1
        }

        long secret = 0;
        int k = shares.size();

        for (int j = 0; j < k; ++j) {
            long yj = shares.get(j).y;
            long basisVal = lagrangeBasisAtZero(shares, j);
            secret = (secret + (yj * basisVal) % PRIME_MODULUS) % PRIME_MODULUS;
        }

        return secret;
    }

    // --- Shape Validation and Reconstruction Logic ---

    /**
     * Filters out wrong shapes by attempting to reconstruct the secret from all
     * combinations of 'k' shares. Shapes participating in the majority reconstructed
     * secret are considered valid.
     *
     * @param allShapes The list of all provided shapes (potentially including wrong ones).
     * @param k         The threshold number of shares required.
     * @return A list of identified valid shapes, or an empty list if no consistent secret is found.
     */
    public static List<Share> filterWrongShapes(List<Share> allShapes, int k) {
        if (k <= 0 || allShapes == null || allShapes.size() < k) {
            System.err.println("Error: Invalid input for filtering.");
            return Collections.emptyList();
        }

        // Store potential valid combinations and their reconstructed secrets
        // Map: secret_value -> count of combinations that yield this secret
        Map<Long, Integer> secretCounts = new HashMap<>();

        // Store the actual combinations that yield the majority secret
        List<List<Share>> potentialValidCombinations = new ArrayList<>();

        // Generate combinations using indices
        List<Integer> indices = IntStream.range(0, allShapes.size()).boxed().collect(Collectors.toList());
        List<Boolean> v = new ArrayList<>(Collections.nCopies(allShapes.size(), false));
        // Mark the first k indices as true for the initial combination
        for (int i = 0; i < k; i++) {
            v.set(i, true);
        }

        // Use a do-while loop to ensure the first combination (0 to k-1) is processed
        do {
            List<Share> currentCombination = new ArrayList<>();
            for (int i = 0; i < allShapes.size(); i++) {
                if (v.get(i)) {
                    currentCombination.add(allShapes.get(i));
                }
            }

            // Reconstruct the secret from this combination
            long reconstructedSecret = reconstructSecret(currentCombination);

            // Update the count for this secret
            secretCounts.put(reconstructedSecret, secretCounts.getOrDefault(reconstructedSecret, 0) + 1);

            // Store this combination if it's a potential candidate
            potentialValidCombinations.add(currentCombination);

        } while (nextCombination(v)); // Custom logic for generating next combination

        // --- Identify the "true" secret and filter ---

        long majoritySecret = -1;
        int maxCount = 0;

        // Find the secret that was reconstructed most often
        for (Map.Entry<Long, Integer> entry : secretCounts.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                majoritySecret = entry.getKey();
            }
        }

        // If no consistent secret is found
        if (majoritySecret == -1) {
            System.err.println("Error: No consistent secret found. Check input shapes or 'k'.");
            return Collections.emptyList();
        }

        // Now, filter the original shapes. A shape is considered "valid" if it
        // participated in at least one combination that reconstructed the majority secret.
        // We use a set to store the IDs of valid shapes to avoid duplicates.
        Map<Integer, Share> validShapesMap = new HashMap<>();

        for (List<Share> combo : potentialValidCombinations) {
            if (reconstructSecret(combo) == majoritySecret) {
                for (Share share : combo) {
                    // Add the share to the map if it's not already there
                    if (!validShapesMap.containsKey(share.id)) {
                        validShapesMap.put(share.id, share);
                    }
                }
            }
        }

        List<Share> validShapes = new ArrayList<>(validShapesMap.values());

        // Double-check: ensure we have at least 'k' valid shapes
        if (validShapes.size() < k) {
            System.err.println("Warning: After filtering, not enough valid shapes ("
                               + validShapes.size() + ") to reconstruct with k=" + k);
            return Collections.emptyList();
        }

        return validShapes;
    }

    // Helper function to generate the next combination (similar to std::next_permutation on a boolean mask)
    private static boolean nextCombination(List<Boolean> v) {
        // Find the rightmost 'false' that has a 'true' to its right
        int i = v.size() - 1;
        while (i >= 0 && v.get(i)) {
            i--;
        }
        if (i < 0) return false; // All 'true's are at the end, no more combinations

        // Find the rightmost 'true' to the right of i
        int j = v.size() - 1;
        while (j > i && !v.get(j)) {
            j--;
        }
        if (j <= i) return false; // Should not happen if i was found correctly

        // Swap false[i] and true[j]
        v.set(i, true);
        v.set(j, false);

        // Reverse the elements from i+1 to the end
        int left = i + 1;
        int right = v.size() - 1;
        while (left < right) {
            Collections.swap(v, left, right);
            left++;
            right--;
        }
        return true;
    }


    // Function to generate shares for testing
    public static List<Share> generateShares(long secret, int k, int numShares, Random random) {
        List<Share> shares = new ArrayList<>();
        Polynomial p = new Polynomial(k);

        // The secret is the constant term
        p.setCoefficient(0, secret);

        // Generate random coefficients for the higher-order terms (up to x^(k-1))
        for (int i = 1; i < k; i++) {
            // Generate a random coefficient within the field modulus
            long randomCoeff = Math.abs(random.nextLong()) % PRIME_MODULUS;
            p.setCoefficient(i, randomCoeff);
        }

        // Generate 'numShares' shares
        for (int i = 0; i < numShares; i++) {
            // Choose distinct, non-zero x values. Use random x values for better simulation.
            long xVal = Math.abs(random.nextLong()) % (PRIME_MODULUS - 1) + 1; // x in [1, PRIME_MODULUS-1]

            // Ensure x values are distinct
            boolean isXDistinct = false;
            while(!isXDistinct) {
                isXDistinct = true;
                for(Share existingShare : shares) {
                    if (existingShare.x == xVal) {
                        xVal = Math.abs(random.nextLong()) % (PRIME_MODULUS - 1) + 1;
                        isXDistinct = false;
                        break;
                    }
                }
            }

            long yVal = p.evaluate(xVal);
            shares.add(new Share(xVal, yVal, i)); // ID is the original index
        }
        return shares;
    }


    // --- Main Example Usage ---
    public static void main(String[] args) {
        int k = 3; // Threshold (need k shares)
        int numTotalShapes = 5; // Total shapes given (m)
        long secretToShare = 12345;
        Random random = new Random(42); // Seed for reproducible randomness

        System.out.println("--- Shamir's Secret Shelling Algorithm ---");
        System.out.println("Original Secret: " + secretToShare);
        System.out.println("Threshold (k): " + k);
        System.out.println("Total shapes (m): " + numTotalShapes);
        System.out.println("Prime Modulus: " + PRIME_MODULUS);
        System.out.println("------------------------------------------");

        // 1. Generate some valid shares and introduce some "wrong" ones.
        List<Share> allShapes = new ArrayList<>();

        // Generate enough valid shares (at least k)
        int numValidSharesToGenerate = Math.max(k, numTotalShapes / 2); // Generate a good number of valid shares
        List<Share> validShares = generateShares(secretToShare, k, numValidSharesToGenerate, random);
        allShapes.addAll(validShares);

        // Introduce "wrong" shapes.
        // A wrong shape is one that doesn't lie on the polynomial defined by the valid shares.
        int currentId = allShapes.size(); // Continue ID assignment

        while (allShapes.size() < numTotalShapes) {
            // Wrong shape 1: Incorrect y-value
            long wrongX = Math.abs(random.nextLong()) % (PRIME_MODULUS - 1) + 1;
            // Ensure this x is not already used by a valid share
            boolean xExists = allShapes.stream().anyMatch(s -> s.x == wrongX);
            while(xExists) {
                 wrongX = Math.abs(random.nextLong()) % (PRIME_MODULUS - 1) + 1;
                 xExists = allShapes.stream().anyMatch(s -> s.x == wrongX);
            }

            long wrongY = (secretToShare + 999 + currentId) % PRIME_MODULUS; // Simple way to make it wrong
            allShapes.add(new Share(wrongX, wrongY, currentId));
            currentId++;
        }

        // Shuffle the shapes to simulate receiving them in a random order
        Collections.shuffle(allShapes, random);

        System.out.println("Provided Shapes:");
        for (Share shape : allShapes) {
            System.out.println("  " + shape);
        }
        System.out.println("------------------------------------------");

        // 2. Filter out wrong shapes
        List<Share> validShapesFiltered = filterWrongShapes(allShapes, k);

        System.out.println("Filtered Valid Shapes (" + validShapesFiltered.size() + " found):");
        if (!validShapesFiltered.isEmpty()) {
            for (Share shape : validShapesFiltered) {
                System.out.println("  " + shape);
            }
            System.out.println("------------------------------------------");

            // 3. Reconstruct the secret using any k valid shapes
            // We assume filterWrongShapes returned enough valid shapes (at least k)
            if (validShapesFiltered.size() >= k) {
                // Take the first k valid shapes for reconstruction
                List<Share> reconstructionShares = validShapesFiltered.subList(0, k);
                long reconstructedSecret = reconstructSecret(reconstructionShares);

                System.out.println("Reconstruction using first " + k + " valid shapes:");
                System.out.println("  Reconstructed Secret: " + reconstructedSecret);

                if (reconstructedSecret == secretToShare) {
                    System.out.println("Success! Secret successfully reconstructed.");
                } else {
                    System.err.println("Failure: Reconstructed secret does not match original.");
                }
            } else {
                System.err.println("Error: Not enough valid shapes (" + validShapesFiltered.size() + ") to reconstruct with k=" + k);
            }
        } else {
            System.err.println("No valid shapes could be identified.");
        }
    }
}
