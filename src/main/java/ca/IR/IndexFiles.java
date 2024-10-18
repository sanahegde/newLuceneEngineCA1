package ca.IR;

import org.apache.lucene.analysis.en.EnglishAnalyzer; // Changed from StandardAnalyzer
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Paths;

public class IndexFiles {

    // Changed analyzer to EnglishAnalyzer
    private static EnglishAnalyzer docAnalyzer = new EnglishAnalyzer();

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: IndexFiles <indexDir> <docsDir>");
            return;
        }

        String indexDirectory = args[0]; // Renamed from indexPath
        String documentsDirectory = args[1]; // Renamed from docsPath

        // Check if documents directory exists
        File docsDir = new File(documentsDirectory);
        if (!docsDir.exists() || !docsDir.isDirectory()) {
            System.out.println("Document directory does not exist or is not a directory: " + documentsDirectory);
            return;
        }

        // Open directory for index storage
        Directory dir = FSDirectory.open(Paths.get(indexDirectory));
        IndexWriterConfig config = new IndexWriterConfig(docAnalyzer);
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);

        try (IndexWriter writer = new IndexWriter(dir, config)) {
            // Index each document in the specified directory
            File[] files = docsDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        System.out.println("Processing file: " + file.getName()); // Changed from "Indexing file"
                        indexDocuments(writer, file);
                    }
                }
            } else {
                System.out.println("No files found in the directory: " + documentsDirectory);
            }
        }

        System.out.println("Indexing completed.");
    }

    // Same method as original but restructured for clarity
    public static void indexDocuments(IndexWriter writer, File file) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            StringBuilder docContent = new StringBuilder();
            int docId = 0;
            String title = ""; // Title initialization

            while ((line = reader.readLine()) != null) {
                if (line.startsWith(".I")) {
                    // Index the previous document if content exists
                    if (docContent.length() > 0) {
                        addDocument(writer, String.valueOf(docId), title, docContent.toString());
                        docContent.setLength(0);
                        title = "";
                    }
                    docId = Integer.parseInt(line.split(" ")[1].trim());
                    System.out.println("Document ID: " + docId); // Changed print statement
                } else if (line.startsWith(".T")) {
                    StringBuilder titleBuilder = new StringBuilder();
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith(".A") || line.startsWith(".B")) {
                            break;
                        }
                        titleBuilder.append(line.trim()).append(" ");
                    }
                    title = titleBuilder.toString().trim();
                    System.out.println("Document Title: " + title); // Changed print statement
                } else if (line.startsWith(".W")) {
                    StringBuilder contentBuilder = new StringBuilder();
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith(".I")) {
                            docContent.append(contentBuilder.toString().trim());
                            addDocument(writer, String.valueOf(docId), title, docContent.toString());
                            docContent.setLength(0);
                            docId = Integer.parseInt(line.split(" ")[1].trim());
                            System.out.println("Next Document ID: " + docId);
                            break;
                        }
                        contentBuilder.append(line.trim()).append(" ");
                    }
                    if (line == null) {
                        docContent.append(contentBuilder.toString().trim());
                        addDocument(writer, String.valueOf(docId), title, docContent.toString());
                    }
                }
            }
        }
    }

    // Method to add the document to Lucene's index
    private static void addDocument(IndexWriter writer, String docID, String title, String textContent)
            throws IOException {
        Document doc = new Document();

        // Index document ID, title, and content
        doc.add(new StringField("documentID", docID, Field.Store.YES));
        doc.add(new TextField("title", title, Field.Store.YES));
        doc.add(new TextField("contents", textContent, Field.Store.YES));

        writer.addDocument(doc);
    }
}
