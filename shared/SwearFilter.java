package shared;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SwearFilter {
    private Set<String> bannedWords;
    private static final String PROFANITY_LIST_PATH = "profanity-list.txt";
    
    // Common delimiters used to bypass filters
    private static final String DELIMITERS = "\\s|\\.|-|_|,|/|\\\\|\\||\\*|\\+|!|@|#|\\$|%|\\^|&|\\(|\\)|=|'|\"|:|;|<|>|\\?";
    
    public SwearFilter() {
        bannedWords = new HashSet<>();
        loadBannedWordsFromFile();
    }
    
    /**
     * Loads banned words from the profanity list file
     */
    private void loadBannedWordsFromFile() {
        try {
            File file = new File(PROFANITY_LIST_PATH);
            if (!file.exists()) {
                System.err.println("Profanity list file not found: " + PROFANITY_LIST_PATH);
                // Add some default words as fallback
                bannedWords.add("badword");
                bannedWords.add("swear");
                bannedWords.add("offensive");
                bannedWords.add("inappropriate");
                bannedWords.add("curse");
                return;
            }
            
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim().toLowerCase();
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        bannedWords.add(line);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error loading profanity list: " + e.getMessage());
            // Add some default words as fallback
            bannedWords.add("badword");
            bannedWords.add("swear");
            bannedWords.add("offensive");
            bannedWords.add("inappropriate");
            bannedWords.add("curse");
        }
    }
    
    /**
     * Checks if the text contains any banned words, including when separated by delimiters
     * or using permutations to bypass filters
     * 
     * @param text The text to check
     * @return true if the text is clean (contains no banned words), false otherwise
     */
    public boolean isClean(String text) {
        if (text == null || text.isEmpty()) {
            return true;
        }
        
        // Convert to lowercase for case-insensitive matching
        String normalizedText = text.toLowerCase();
        
        // Check for banned words with delimiters
        for (String bannedWord : bannedWords) {
            // Create a pattern that allows delimiters between characters
            StringBuilder patternBuilder = new StringBuilder();
            for (int i = 0; i < bannedWord.length(); i++) {
                patternBuilder.append(bannedWord.charAt(i));
                // Add optional delimiters between characters, but not after the last one
                if (i < bannedWord.length() - 1) {
                    patternBuilder.append("(?:").append(DELIMITERS).append(")*");
                }
            }
            
            // Compile the pattern and check for matches
            Pattern pattern = Pattern.compile(patternBuilder.toString());
            Matcher matcher = pattern.matcher(normalizedText);
            
            if (matcher.find()) {
                return false;
            }
        }
        
        // Also check for the original banned words as substrings
        // This catches cases where banned words are embedded in other words
        for (String bannedWord : bannedWords) {
            if (normalizedText.contains(bannedWord)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Filters text by replacing banned words with asterisks,
     * including when they're separated by delimiters
     * 
     * @param text The text to filter
     * @return Filtered text with banned words replaced by asterisks
     */
    public String filter(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        
        String result = text;
        
        // First handle direct matches
        for (String bannedWord : bannedWords) {
            // Case-insensitive replacement
            Pattern pattern = Pattern.compile(bannedWord, Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(result);
            
            result = matcher.replaceAll(s -> "*".repeat(s.group().length()));
        }
        
        // Then handle delimiter-separated matches
        for (String bannedWord : bannedWords) {
            // Create a pattern that allows delimiters between characters
            StringBuilder patternBuilder = new StringBuilder();
            for (int i = 0; i < bannedWord.length(); i++) {
                patternBuilder.append(bannedWord.charAt(i));
                // Add optional delimiters between characters, but not after the last one
                if (i < bannedWord.length() - 1) {
                    patternBuilder.append("(?:").append(DELIMITERS).append(")*");
                }
            }
            
            // Compile the pattern and replace matches
            Pattern pattern = Pattern.compile(patternBuilder.toString(), Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(result);
            
            result = matcher.replaceAll(s -> "*".repeat(s.group().length()));
        }
        
        return result;
    }
    
    /**
     * Adds a banned word to the filter
     * @param word The word to ban
     */
    public void addBannedWord(String word) {
        bannedWords.add(word.toLowerCase());
    }
}
