import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.regex.*;

public class BMIGui extends JFrame {
    private JTextField weightField;
    private JTextField heightField;
    private JButton calcButton;
    private JTextArea resultArea;
    private JLabel statusLabel;

    public BMIGui() {
        setTitle("BMI Calculator - Client");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(420, 340);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        
        JPanel top = new JPanel(new GridBagLayout());
        top.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        top.add(new JLabel("Weight (kg):"), gbc);
        gbc.gridx = 1;
        weightField = new JTextField(12);
        top.add(weightField, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        top.add(new JLabel("Height (cm):"), gbc);
        gbc.gridx = 1;
        heightField = new JTextField(12);
        top.add(heightField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        calcButton = new JButton("Calculate BMI");
        top.add(calcButton, gbc);

        add(top, BorderLayout.NORTH);

        
        resultArea = new JTextArea();
        resultArea.setEditable(false);
        resultArea.setLineWrap(true);
        resultArea.setWrapStyleWord(true);
        resultArea.setFont(new Font("SansSerif", Font.PLAIN, 14));
        JScrollPane sp = new JScrollPane(resultArea);
        sp.setBorder(BorderFactory.createTitledBorder("Result"));
        add(sp, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        statusLabel = new JLabel("Ready");
        bottom.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
        bottom.add(statusLabel, BorderLayout.WEST);
        add(bottom, BorderLayout.SOUTH);

        
        calcButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCalculate();
            }
        });

        // Enter key triggers calculate
        heightField.addActionListener(ae -> onCalculate());
        weightField.addActionListener(ae -> onCalculate());
    }

    private void onCalculate() {
        String sWeight = weightField.getText().trim();
        String sHeight = heightField.getText().trim();

        if (sWeight.isEmpty() || sHeight.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter both weight and height.", "Input required", JOptionPane.WARNING_MESSAGE);
            return;
        }

        double weight, height;
        try {
            weight = Double.parseDouble(sWeight);
            height = Double.parseDouble(sHeight);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Please enter numeric values (e.g. 70 or 175).", "Invalid input", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (weight <= 0 || height <= 0) {
            JOptionPane.showMessageDialog(this, "Values must be greater than zero.", "Invalid input", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // disable UI while processing
        setUiEnabled(false);
        statusLabel.setText("Sending request...");
        resultArea.setText("");

        // run network request in background thread to keep UI responsive
        new Thread(() -> {
            try {
                String jsonRequest = "{\"weight\": " + weight + ", \"height\": " + height + "}";
                String response = postJson("http://127.0.0.1:5000/api/bmi", jsonRequest);

                if (response == null) {
                    showError("No response from server.");
                } else {
                    // Parse simple JSON response (no external libs). This expects keys:
                    // "bmi" (number), "category" (string), "advice" (string)
                    Double bmi = extractNumber(response, "\"bmi\"\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)");
                    String category = extractString(response, "\"category\"\\s*:\\s*\"(.*?)\"");
                    String advice = extractString(response, "\"advice\"\\s*:\\s*\"(.*?)\"");

                    StringBuilder out = new StringBuilder();
                    out.append("Server response:\n");
                    out.append(response).append("\n\n");
                    if (bmi != null) out.append(String.format("BMI: %.2f\n", bmi));
                    if (category != null) out.append("Category: " + category + "\n");
                    if (advice != null) out.append("Advice: " + advice + "\n");

                    if (bmi == null && category == null && advice == null) {
                        out.append("\nWarning: Could not parse server response reliably.");
                    }

                    final String display = out.toString();
                    SwingUtilities.invokeLater(() -> {
                        resultArea.setText(display);
                        statusLabel.setText("Done");
                    });
                }
            } catch (Exception ex) {
                showError("Error: " + ex.getMessage());
            } finally {
                SwingUtilities.invokeLater(() -> setUiEnabled(true));
            }
        }).start();
    }

    private void setUiEnabled(boolean enabled) {
        weightField.setEnabled(enabled);
        heightField.setEnabled(enabled);
        calcButton.setEnabled(enabled);
    }

    private void showError(String message) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("Error");
            JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
        });
    }

    private String postJson(String urlString, String jsonPayload) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(7000);
            conn.setRequestProperty("Content-Type", "application/json; utf-8");
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonPayload.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            int status = conn.getResponseCode();
            InputStream is = (status >= 200 && status < 300) ? conn.getInputStream() : conn.getErrorStream();
            if (is == null) return null;

            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, "utf-8"))) {
                StringBuilder resp = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    resp.append(line.trim());
                }
                return resp.toString();
            }
        } finally {
            conn.disconnect();
        }
    }

    private Double extractNumber(String text, String regex) {
        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher(text);
        if (m.find()) {
            try {
                return Double.parseDouble(m.group(1));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private String extractString(String text, String regex) {
        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher(text);
        if (m.find()) {
            // unescape common escape sequences for safety
            String s = m.group(1);
            s = s.replace("\\\"", "\"").replace("\\n", "\n").replace("\\t", "\t").replace("\\\\", "\\");
            return s;
        }
        return null;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            BMIGui gui = new BMIGui();
            gui.setVisible(true);
        });
    }
}
