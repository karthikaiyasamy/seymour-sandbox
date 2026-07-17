using System.Text.RegularExpressions;

namespace LangleyGeneralGateway.Utils
{
    public static class PhnValidator
    {
        // BC PHNs are strictly 10 digits and start with a 9
        private static readonly Regex PhnRegex = new(@"^9\d{9}$", RegexOptions.Compiled);

        /// <summary>
        /// Validates a BC PHN using the standard Modulus-11 check digit algorithm.
        /// </summary>
        public static bool IsValidBCOnlyPHN(string? phn)
        {
            if (string.IsNullOrWhiteSpace(phn) || !PhnRegex.IsMatch(phn))
            {
                return false;
            }

            // Modulus 11 weights for BC Health Card validation (Digits 2 through 9)
            int[] weights = { 2, 4, 8, 5, 10, 9, 7, 3 };
            int sum = 0;

            // Loop index 1 through 8 (representing digits 2 through 9)
            for (int i = 1; i < 9; i++)
            {
                int digit = phn[i] - '0'; // safe ASCII conversion
                sum += digit * weights[i - 1];
            }

            int remainder = sum % 11;

            // If remainder is 0 or 1, the check digit would be 11 or 10, which is invalid
            if (remainder is 0 or 1)
            {
                return false;
            }

            int checkDigit = 11 - remainder;
            int providedCheckDigit = phn[9] - '0';

            return checkDigit == providedCheckDigit;
        }

        /// <summary>
        /// Masks the sensitive middle digits of a PHN for unsafe outputs/logs.
        /// Replaces characters index 3 through 6 with asterisks.
        /// e.g., "9123456789" -> "912****789"
        /// </summary>
        public static string MaskPHN(string? phn)
        {
            if (string.IsNullOrWhiteSpace(phn) || phn.Length != 10)
            {
                return "**********"; // Fallback safe mask
            }
            return string.Concat(phn[..3], "****", phn[7..]);
        }
    }
}
