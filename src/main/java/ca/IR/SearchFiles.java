package ca.IR;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.BooleanSimilarity;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.store.FSDirectory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class SearchFiles {
    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.out.println("Usage: SearchFiles <indexDir> <queriesFile> <scoreType> <outputFile>");
            return;
        }

        String indexPath = args[0];
        String queriesPath = args[1];
        int scoreType = Integer.parseInt(args[2]);
        String outputPath = args[3];

        try (DirectoryReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexPath)));
             PrintWriter writer = new PrintWriter(new FileWriter(outputPath))) {

            IndexSearcher searcher = new IndexSearcher(reader);
            setSimilarity(searcher, scoreType);

            StandardAnalyzer analyzer = new StandardAnalyzer();
            String[] fields = {"title", "contents"};
            Map<String, Float> boosts = new HashMap<>();
            boosts.put("title", 3.0f);  // Increase the boost for the title field
            boosts.put("contents", 1.0f);

            MultiFieldQueryParser parser = new MultiFieldQueryParser(fields, analyzer, boosts);

            // Read queries in .nqry format
            parseQueries(queriesPath, parser, searcher, writer);
        }

        System.out.println("Search completed. Results written to: " + outputPath);
    }

    private static void parseQueries(String queriesPath, MultiFieldQueryParser parser, IndexSearcher searcher, PrintWriter writer) {
        File queryFile = new File(queriesPath);

        // Check if the file exists and is readable
        if (!queryFile.exists() || !queryFile.canRead()) {
            System.out.println("Query file not found or is not readable: " + queriesPath);
            return; // Exit the method if the file is not valid
        }

        try (Scanner scanner = new Scanner(queryFile)) {
            int queryNumber = 1;
            StringBuilder queryBuilder = new StringBuilder();
            boolean readingQuery = false;

            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();

                if (line.startsWith(".I")) {
                    // If there's content from a previous document, process it
                    if (readingQuery) {
                        processQuery(queryBuilder.toString(), queryNumber, parser, searcher, writer);
                        queryBuilder.setLength(0); // Clear the query buffer
                    }
                    queryNumber++; // Increment the query number
                    readingQuery = false; // Reset reading query flag
                } else if (line.startsWith(".W")) {
                    readingQuery = true; // Start reading the query text
                } else if (readingQuery) {
                    // Accumulate query text lines
                    queryBuilder.append(line).append(" ");
                }
            }
            // Process the last query if exists
            if (queryBuilder.length() > 0) {
                processQuery(queryBuilder.toString(), queryNumber, parser, searcher, writer);
            }
        } catch (FileNotFoundException e) {
            System.out.println("Error reading the query file: " + e.getMessage());
        }
    }


    // Process a query and perform the search
    private static void processQuery(String queryString, int queryNumber, MultiFieldQueryParser parser, IndexSearcher searcher, PrintWriter writer) {
        try {
            queryString = escapeSpecialCharacters(queryString.trim()); // Escape special characters
            Query query = parser.parse(queryString);

            // Run the search and retrieve top 100 results
            ScoreDoc[] hits = searcher.search(query, 50).scoreDocs;

            int rank = 1;
            for (ScoreDoc hit : hits) {
                Document doc = searcher.doc(hit.doc);

                // Fetching the indexed 'documentID' instead of internal docId
                String docID = doc.get("documentID");

                // Write result in TREC format: queryNumber Q0 docID rank score runTag
                writer.println(queryNumber + " 0 " + docID + " " + rank + " " + hit.score + " STANDARD");
                rank++;
            }
        } catch (Exception e) {
            System.out.println("Error parsing query: " + queryString);
        }
    }

    // Method to set the similarity model
    private static void setSimilarity(IndexSearcher searcher, int scoreType) {
        switch (scoreType) {
            case 0:
                searcher.setSimilarity(new ClassicSimilarity()); // Classic TF-IDF
                break;
            case 1:
                searcher.setSimilarity(new BM25Similarity(1.5f, 0.75f)); // Tuned BM25
                break;
            case 2:
                searcher.setSimilarity(new BooleanSimilarity()); // Boolean
                break;
            default:
                throw new IllegalArgumentException("Invalid score type: " + scoreType);
        }
    }

    // Method to escape special characters in queries
    public static String escapeSpecialCharacters(String queryString) {
        String[] specialChars = {"\\", "+", "-", "&&", "||", "!", "(", ")", "{", "}", "[", "]", "^", "\"", "~", "*", "?", ":", "/"};
        for (String specialChar : specialChars) {
            queryString = queryString.replace(specialChar, "\\" + specialChar);
        }
        return queryString;
    }
}
