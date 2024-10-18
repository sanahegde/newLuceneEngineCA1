package ca.IR;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
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

    private static StandardAnalyzer analyzer = new StandardAnalyzer();

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: IndexFiles <indexDir> <docsDir>");
            return;
        }

        String indexPath = args[0];  // Directory to save the index
        String docsPath = args[1];   // Directory of the documents

        // Check if documents directory exists
        File docsDir = new File(docsPath);
        if (!docsDir.exists() || !docsDir.isDirectory()) {
            System.out.println("Document directory does not exist or is not a directory: " + docsPath);
            return;
        }

        // Open directory for index storage
        Directory dir = FSDirectory.open(Paths.get(indexPath));
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);

        try (IndexWriter writer = new IndexWriter(dir, config)) {
            // Index each document in the specified directory
            File[] files = docsDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        System.out.println("Indexing file: " + file.getName());
                        indexDocuments(writer, file);  // Fixed method signature
                    }
                }
            } else {
                System.out.println("No files found in the directory: " + docsPath);
            }
        }

        System.out.println("Indexing completed.");
    }

    // Corrected to match the expected file type and the IndexWriter
    public static void indexDocuments(IndexWriter writer, File file) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            StringBuilder docContent = new StringBuilder();
            int docId = 0;
            String title = "";  // Initialize title variable
            while ((line = reader.readLine()) != null) {
                if (line.startsWith(".I")) {
                    // If there's content from a previous document, index it
                    if (docContent.length() > 0) {
                        addDocument(writer, String.valueOf(docId), title, docContent.toString());
                        docContent.setLength(0); // Clear the content buffer
                        title = "";  // Reset title for the next document
                    }
                    // Read the new document ID
                    docId = Integer.parseInt(line.split(" ")[1].trim());
                    System.out.println("docId: " + docId);
                } else if (line.startsWith(".T")) {  // Capture title if present
                    StringBuilder titleBuilder = new StringBuilder();
                    // Read subsequent lines for the title
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith(".A") || line.startsWith(".B")) {  // End of title section
                            break;  // Exit loop when encountering the next marker
                        }
                        titleBuilder.append(line.trim()).append(" ");  // Accumulate title lines
                    }
                    title = titleBuilder.toString().trim();  // Capture title
                    System.out.println("Title: " + title);
                } else if (line.startsWith(".W")) {  // Start of the document content
                    StringBuilder contentBuilder = new StringBuilder();
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith(".I")) {  // End of content section and beginning of new document
                            docContent.append(contentBuilder.toString().trim());  // Append content
                            addDocument(writer, String.valueOf(docId), title, docContent.toString());  // Index the document
                            docContent.setLength(0);  // Clear content buffer for next doc
                            docId = Integer.parseInt(line.split(" ")[1].trim());  // Read new document ID
                            System.out.println("New docId: " + docId);  // Process the next docId
                            break;  // Exit inner loop to process next document
                        }
                        contentBuilder.append(line.trim()).append(" ");  // Accumulate content
                    }
                    if (line == null) {
                        // If end of file, index the last document
                        docContent.append(contentBuilder.toString().trim());
                        addDocument(writer, String.valueOf(docId), title, docContent.toString());  // Index last document
                    }
                }
            }
        }
    }

    // Method to add the document to Lucene's index
    private static void addDocument(IndexWriter writer, String docID, String title, String textContent) throws IOException {
        Document doc = new Document();

        // Index the document ID, title, and content
        doc.add(new StringField("documentID", docID, Field.Store.YES));
        doc.add(new TextField("title", title, Field.Store.YES));
        doc.add(new TextField("contents", textContent, Field.Store.YES));

        writer.addDocument(doc);
    }
}
