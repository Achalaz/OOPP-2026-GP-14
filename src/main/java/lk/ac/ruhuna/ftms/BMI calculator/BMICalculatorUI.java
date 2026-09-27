import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Locale;

public class BMICalculatorUI extends JFrame {

    private final BMIModel model = new BMIModel();

    private final JRadioButton metricButton =
            new JRadioButton("Metric (kg / m)", true);

    private final JRadioButton englishButton =
            new JRadioButton("English (lb / in)");

    private final JLabel weightLabel = new JLabel("Weight (kg):");
    private final JLabel heightLabel = new JLabel("Height (m):");

    private final JTextField weightField = new JTextField(15);
    private final JTextField heightField = new JTextField(15);

    private final JLabel bmiLabel = new JLabel("Your BMI: --");
    private final JLabel categoryLabel = new JLabel("Category: --");

    public BMICalculatorUI() {

        // Configure the application window.
        setTitle("BMI Calculator");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

        JLabel title = new JLabel("BMI Calculator");
        title.setFont(new Font("Arial", Font.BOLD, 26));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        mainPanel.add(title);
        mainPanel.add(Box.createVerticalStrut(20));

        // Allow only one unit system to be selected.
        ButtonGroup unitGroup = new ButtonGroup();
        unitGroup.add(metricButton);
        unitGroup.add(englishButton);

        JPanel unitPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        unitPanel.add(new JLabel("Units:"));
        unitPanel.add(metricButton);
        unitPanel.add(englishButton);

        mainPanel.add(unitPanel);
        mainPanel.add(Box.createVerticalStrut(10));

        // Create the input section.
        JPanel inputPanel = new JPanel(new GridLayout(2, 2, 10, 15));
        inputPanel.add(weightLabel);
        inputPanel.add(weightField);
        inputPanel.add(heightLabel);
        inputPanel.add(heightField);

        mainPanel.add(inputPanel);
        mainPanel.add(Box.createVerticalStrut(20));

        JButton calculateButton = new JButton("Calculate BMI");
        JButton clearButton = new JButton("Clear");

        JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        buttonPanel.add(calculateButton);
        buttonPanel.add(clearButton);

        mainPanel.add(buttonPanel);
        mainPanel.add(Box.createVerticalStrut(20));

        // Create the result section.
        JPanel resultPanel = new JPanel(new GridLayout(2, 1, 0, 10));
        resultPanel.setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createTitledBorder("Result"),
                        new EmptyBorder(10, 10, 10, 10)
                )
        );

        bmiLabel.setFont(new Font("Arial", Font.BOLD, 18));
        categoryLabel.setFont(new Font("Arial", Font.BOLD, 18));

        resultPanel.add(bmiLabel);
        resultPanel.add(categoryLabel);

        mainPanel.add(resultPanel);
        mainPanel.add(Box.createVerticalStrut(20));

        // Display the reference ranges from the practical sheet.
        String[] columns = {"Category", "BMI"};

        String[][] rows = {
                {"Underweight", "Less than 18.5"},
                {"Normal", "18.5 - 24.9"},
                {"Overweight", "25 - 29.9"},
                {"Obese", "30 or greater"}
        };

        JTable referenceTable = new JTable(rows, columns) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        referenceTable.setRowHeight(28);
        referenceTable.getTableHeader().setReorderingAllowed(false);

        JPanel referencePanel = new JPanel(new BorderLayout());
        referencePanel.setBorder(
                BorderFactory.createTitledBorder("BMI Reference")
        );
        referencePanel.add(
                referenceTable.getTableHeader(),
                BorderLayout.NORTH
        );
        referencePanel.add(referenceTable, BorderLayout.CENTER);

        mainPanel.add(referencePanel);

        // Register button actions.
        calculateButton.addActionListener(event -> calculateBMI());
        clearButton.addActionListener(event -> clearFields());

        metricButton.addActionListener(event -> changeUnits());
        englishButton.addActionListener(event -> changeUnits());

        // Allow Enter to calculate BMI.
        getRootPane().setDefaultButton(calculateButton);

        setContentPane(mainPanel);
        pack();
        setResizable(false);
        setLocationRelativeTo(null);
    }

    private void changeUnits() {

        if (metricButton.isSelected()) {
            weightLabel.setText("Weight (kg):");
            heightLabel.setText("Height (m):");
        } else {
            weightLabel.setText("Weight (lb):");
            heightLabel.setText("Height (in):");
        }

        // Clear old values when changing units.
        clearFields();
    }

    private void calculateBMI() {

        resetResult();

        String weightText = weightField.getText().trim();
        String heightText = heightField.getText().trim();

        // Check for missing inputs.
        if (weightText.isEmpty() || heightText.isEmpty()) {
            showError("Please enter both weight and height.");
            return;
        }

        try {
            double weight = Double.parseDouble(weightText);
            double height = Double.parseDouble(heightText);

            double bmi = model.calculateBMI(
                    weight,
                    height,
                    metricButton.isSelected()
            );

            bmiLabel.setText(
                    String.format(Locale.US, "Your BMI: %.2f", bmi)
            );

            categoryLabel.setText(
                    "Category: " + model.getCategory(bmi)
            );

        } catch (NumberFormatException e) {
            showError("Please enter valid numbers only.");

        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
        }
    }

    private void clearFields() {

        // Clear inputs and reset the displayed result.
        weightField.setText("");
        heightField.setText("");
        resetResult();
        weightField.requestFocusInWindow();
    }

    private void resetResult() {
        bmiLabel.setText("Your BMI: --");
        categoryLabel.setText("Category: --");
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(
                this,
                message,
                "Invalid Input",
                JOptionPane.ERROR_MESSAGE
        );
    }
}