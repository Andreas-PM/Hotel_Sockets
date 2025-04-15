package client;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public class SwearFilter {
    private Set<String> bannedWords = new HashSet<>();

    public SwearFilter() {
        loadBannedWords("profanity-list.txt");
    }


    private void loadBannedWords(String resourceName) {
        try (InputStream inputStream = getClass().getResourceAsStream(resourceName);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {

            if (inputStream == null) {
                System.err.println("Resource not found: " + resourceName);
                return;
            }
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    bannedWords.add(line);
                }
            }
        } catch (IOException e) {
            System.err.println("Error loading banned words: " + e.getMessage());
        }
    }

    public String filter(String message) {
        String filteredMessage = message;
        for (String word : bannedWords) {
            // Build the regex pattern for whole-word, case-insensitive matching.
            String regex = "(?i)\\b" + Pattern.quote(word) + "\\b";
            filteredMessage = filteredMessage.replaceAll(regex, mask(word.length()));
        }
        return filteredMessage;
    }

    // Helper method to generate a string of asterisks that matches the length of a word.
    private String mask(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append("*");
        }
        return sb.toString();
    }

    // For quick testing.
    public static void main(String[] args) {
        SwearFilter filter = new SwearFilter();
        String testMessage = "This is a test that might include words like fuck or shit.";
        System.out.println("Original: " + testMessage);
        System.out.println("Filtered: " + filter.filter(testMessage));
    }
}
