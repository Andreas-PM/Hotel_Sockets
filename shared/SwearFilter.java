package shared;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class SwearFilter {
    private Set<String> bannedWords;
    private static final String PROFANITY_LIST_PATH = "profanity-list.txt";
    
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
     * Checks if the text contains any banned words
     * @param text The text to check
     * @return true if the text is clean (contains no banned words), false otherwise
     */
    public boolean isClean(String text) {
        if (text == null || text.isEmpty()) {
            return true;
        }
        
        String[] words = text.split("\\s+");
        for (String word : words) {
            if (bannedWords.contains(word.toLowerCase())) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Filters text by replacing banned words with asterisks
     * @param text The text to filter
     * @return Filtered text with banned words replaced by asterisks
     */
    public String filter(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        
        String[] words = text.split("\\s+");
        StringBuilder filtered = new StringBuilder();
        
        for (String word : words) {
            // Check if the word (lowercase) is in the banned list
            if (bannedWords.contains(word.toLowerCase())) {
                // Replace with asterisks
                filtered.append("*".repeat(word.length()));
            } else {
                filtered.append(word);
            }
            filtered.append(" ");
        }
        
        // Trim the trailing space and return
        return filtered.toString().trim();
    }
    
    /**
     * Adds a banned word to the filter
     * @param word The word to ban
     */
    public void addBannedWord(String word) {
        bannedWords.add(word.toLowerCase());
    }
}
