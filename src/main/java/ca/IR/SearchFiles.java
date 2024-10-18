package ca.IR;

import org.apache.lucene.analysis.en.EnglishAnalyzer; // Changed from StandardAnalyzer
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

        String indexDirectory = args[0];
        String queryFilePath = args[1];
        int similarityModel = Integer.parseInt(args[2]);
        String resultFilePath = args[3];

        try (DirectoryReader indexReader = DirectoryReader.open(FSDirectory.open(Paths.get(indexDirectory)));
                PrintWriter outputWriter = new PrintWriter(new FileWriter(resultFilePath))) {

            IndexSearcher indexSearcher = new IndexSearcher(indexReader);
            applySimilarityModel(indexSearcher, similarityModel);

            EnglishAnalyzer docAnalyzer = new EnglishAnalyzer(); // Changed to EnglishAnalyzer
            String[] searchFields = { "title", "contents" };
            Map<String, Float> fieldBoosts = new HashMap<>();
            fieldBoosts.put("title", 3.0f); // Title field given higher importance
            fieldBoosts.put("contents", 1.0f);

            MultiFieldQueryParser queryParser = new MultiFieldQueryParser(searchFields, docAnalyzer, fieldBoosts);

            // Process queries from the query file
            processQueries(queryFilePath, queryParser, indexSearcher, outputWriter);
        }

        System.out.println("Query execution completed. Results written to: " + resultFilePath);
    }

    private static void processQueries(String queryFilePath, MultiFieldQueryParser queryParser,
            IndexSearcher indexSearcher, PrintWriter outputWriter) {
        File queryFile = new File(queryFilePath);

        // Validate file existence and readability
        if (!queryFile.exists() || !queryFile.canRead()) {
            System.out.println("Query file not found or not readable: " + queryFilePath);
            return; // Exit if file is invalid
        }

        try (Scanner queryScanner = new Scanner(queryFile)) {
            int queryID = 1; // Renamed variable
            StringBuilder queryContentBuilder = new StringBuilder();
            boolean isReadingQuery = false;

            while (queryScanner.hasNextLine()) {
                String currentLine = queryScanner.nextLine().trim();

                if (currentLine.startsWith(".I")) {
                    // Process the previous query if there's content
                    if (isReadingQuery) {
                        executeQuery(queryContentBuilder.toString(), queryID, queryParser, indexSearcher, outputWriter);
                        queryContentBuilder.setLength(0); // Clear the builder
                    }
                    queryID++; // Move to the next query
                    isReadingQuery = false;
                } else if (currentLine.startsWith(".W")) {
                    isReadingQuery = true; // Start reading query
                } else if (isReadingQuery) {
                    queryContentBuilder.append(currentLine).append(" "); // Accumulate query text
                }
            }
            // Process the final query if exists
            if (queryContentBuilder.length() > 0) {
                executeQuery(queryContentBuilder.toString(), queryID, queryParser, indexSearcher, outputWriter);
            }
        } catch (FileNotFoundException e) {
            System.out.println("Error reading the query file: " + e.getMessage());
        }
    }

    // Perform the query execution and write results
    private static void executeQuery(String queryText, int queryID, MultiFieldQueryParser queryParser,
            IndexSearcher indexSearcher, PrintWriter outputWriter) {
        try {
            queryText = escapeSpecialChars(queryText.trim()); // Escape special characters
            Query parsedQuery = queryParser.parse(queryText);

            // Perform the search and retrieve top 50 results
            ScoreDoc[] searchResults = indexSearcher.search(parsedQuery, 50).scoreDocs;

            int rank = 1;
            for (ScoreDoc result : searchResults) {
                Document document = indexSearcher.doc(result.doc);
                String documentID = document.get("documentID"); // Fetch document ID

                // Output in TREC format: queryID Q0 docID rank score runTag
                outputWriter.println(queryID + " 0 " + documentID + " " + rank + " " + result.score + " STANDARD");
                rank++;
            }
        } catch (Exception e) {
            System.out.println("Error parsing query: " + queryText);
        }
    }

    // Set similarity model based on input
    private static void applySimilarityModel(IndexSearcher indexSearcher, int modelType) {
        switch (modelType) {
            case 0:
                indexSearcher.setSimilarity(new ClassicSimilarity()); // TF-IDF
                break;
            case 1:
                indexSearcher.setSimilarity(new BM25Similarity(1.5f, 0.75f)); // BM25 with custom parameters
                break;
            case 2:
                indexSearcher.setSimilarity(new BooleanSimilarity()); // Boolean similarity
                break;
            default:
                throw new IllegalArgumentException("Invalid similarity model type: " + modelType);
        }
    }

    // Escape special characters in query text
    public static String escapeSpecialChars(String queryText) {
        String[] specialChars = { "\\", "+", "-", "&&", "||", "!", "(", ")", "{", "}", "[", "]", "^", "\"", "~", "*",
                "?", ":", "/" };
        for (String specialChar : specialChars) {
            queryText = queryText.replace(specialChar, "\\" + specialChar);
        }
        return queryText;
    }
}
