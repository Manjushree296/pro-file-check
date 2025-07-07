package com.resume.resumeanalyzer.controller;
import org.apache.tika.Tika;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.ResponseEntity;
import com.google.firebase.cloud.FirestoreClient;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.QuerySnapshot;
import java.text.SimpleDateFormat;
import java.util.*;

@RestController
public class ResumeMatcherController {
    private double calculateCosineSimilarity(String text1, String text2) {
        Map<String, Integer> freq1 = getWordFreq(text1);
        Map<String, Integer> freq2 = getWordFreq(text2);

        Set<String> allWords = new HashSet<>();
        allWords.addAll(freq1.keySet());
        allWords.addAll(freq2.keySet());

        int dotProduct = 0;
        double magnitude1 = 0;
        double magnitude2 = 0;

        for (String word : allWords) {
            int f1 = freq1.getOrDefault(word, 0);
            int f2 = freq2.getOrDefault(word, 0);

            dotProduct += f1 * f2;
            magnitude1 += f1 * f1;
            magnitude2 += f2 * f2;
        }

        if (magnitude1 == 0 || magnitude2 == 0) return 0.0;
        return dotProduct / (Math.sqrt(magnitude1) * Math.sqrt(magnitude2));
    }

    private Map<String, Integer> getWordFreq(String text) {
        Map<String, Integer> freq = new HashMap<>();
        String[] words = text.toLowerCase().replaceAll("[^a-z ]", "").split("\\s+");

        for (String word : words) {
            if (word.length() > 2 && !isStopWord(word)) {
                freq.put(word, freq.getOrDefault(word, 0) + 1);
            }
        }
        return freq;
    }

    private boolean isStopWord(String word) {
        List<String> stopWords = List.of("the", "and", "for", "with", "from", "that", "are", "this", "you", "your");
        return stopWords.contains(word);
    }


    @PostMapping("/compare")
    public String compareFiles(@RequestParam("email") String email,
                               @RequestParam("resume") MultipartFile resumeFile,
                               @RequestParam("jd") MultipartFile jdFile) {
        try {
            Tika tika = new Tika();
            String resumeText = tika.parseToString(resumeFile.getInputStream());
            String jdText = tika.parseToString(jdFile.getInputStream());

            double similarityScore = calculateCosineSimilarity(resumeText, jdText);

            Firestore db = FirestoreClient.getFirestore();
            Map<String, Object> data = new HashMap<>();
            data.put("email", email);
            data.put("matchScore", similarityScore);
            data.put("timestamp", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
            data.put("resumeContent", resumeText);
            data.put("jobDescription", jdText);

            db.collection("analysis_results").add(data);

            return "Similarity Score: " + String.format("%.2f", similarityScore * 100) + "%";
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @GetMapping("/results")
    public ResponseEntity<List<Map<String, Object>>> getResults(@RequestParam String email) {
        try {
            Firestore db = FirestoreClient.getFirestore();
            List<Map<String, Object>> results = new ArrayList<>();

            ApiFuture<QuerySnapshot> future = db.collection("analysis_results")
                    .whereEqualTo("email", email)
                    .get();

            for (DocumentSnapshot doc : future.get().getDocuments()) {
                Map<String, Object> data = doc.getData();
                data.put("id", doc.getId()); // add document ID for delete
                results.add(data);
            }

            return ResponseEntity.ok(results);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).build();
        }
    }

    @DeleteMapping("/results/{id}")
    public ResponseEntity<String> deleteResult(@PathVariable String id) {
        try {
            Firestore db = FirestoreClient.getFirestore();
            db.collection("analysis_results").document(id).delete();
            return ResponseEntity.ok("Deleted");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error deleting result");
        }
    }


    @PostMapping("/signup")
    public ResponseEntity<String> signup(@RequestBody Map<String, String> userData) {
        String name = userData.get("name");
        String email = userData.get("email");

        Firestore db = FirestoreClient.getFirestore();
        Map<String, String> user = new HashMap<>();
        user.put("name", name);
        user.put("email", email);

        db.collection("users").document(email).set(user);
        return ResponseEntity.ok("Signed up");
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody Map<String, String> data) {
        String email = data.get("email");

        try {
            Firestore db = FirestoreClient.getFirestore();
            DocumentReference docRef = db.collection("users").document(email);
            ApiFuture<DocumentSnapshot> future = docRef.get();
            DocumentSnapshot document = future.get();

            if (!document.exists()) {
                return ResponseEntity.status(401).build(); // Not found
            }

            Map<String, String> user = document.toObject(Map.class);
            return ResponseEntity.ok(user);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).build();
        }
    }


}
