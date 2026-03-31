package info.openrocket.swing.gui.dialogs;

import java.awt.Color;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.rocketcomponent.BodyTube;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.unit.UnitGroup;
import info.openrocket.core.util.EjectionChargeCalculator;
import info.openrocket.swing.gui.util.GUIUtil;

import net.miginfocom.swing.MigLayout;

/**
 * Dialog for calculating black powder ejection charges for dual-deployment
 * recovery systems. Allows the user to specify airframe dimensions and target
 * pressure, then computes the recommended BP charge mass.
 *
 * <p>Accessible from the Tools menu in the main application frame.
 *
 * @author Alex Zoghlin
 */
public class EjectionChargeDialog extends JDialog {
	private static final long serialVersionUID = 1L;

	private final OpenRocketDocument document;

	// Input components
	private JComboBox<BodyTubeItem> tubeSelector;
	private JSpinner diameterSpinner;
	private JSpinner lengthSpinner;
	private JSlider pressureSlider;
	private JLabel pressureValueLabel;

	// Output labels
	private JLabel volumeLabel;
	private JLabel bpMassLabel;
	private JLabel bpRangeLabel;
	private JLabel bpOuncesLabel;
	private JLabel bpTeaspoonsLabel;

	// Unit toggle
	private boolean useMetric = true;

	// Body tubes found in the design
	private final List<BodyTubeItem> bodyTubes = new ArrayList<>();

	/**
	 * Wrapper for BodyTube to display in combo box.
	 */
	private static class BodyTubeItem {
		final BodyTube tube;
		final String label;

		BodyTubeItem(BodyTube tube, String label) {
			this.tube = tube;
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	public EjectionChargeDialog(Window owner, OpenRocketDocument document) {
		super(owner, "Ejection Charge Calculator", ModalityType.MODELESS);
		this.document = document;

		// Determine if the user prefers metric or imperial based on length unit default
		String defaultLengthUnit = UnitGroup.UNITS_LENGTH.getDefaultUnit().getUnit();
		useMetric = !defaultLengthUnit.contains("in") && !defaultLengthUnit.contains("ft");

		scanBodyTubes();
		buildUI();
		recalculate();

		pack();
		setLocationRelativeTo(owner);
		GUIUtil.setDisposableDialogOptions(this, null);
	}

	/**
	 * Scan the rocket design for BodyTube components.
	 */
	private void scanBodyTubes() {
		if (document == null) return;
		Rocket rocket = document.getRocket();
		if (rocket == null) return;

		for (RocketComponent comp : rocket) {
			if (comp instanceof BodyTube) {
				BodyTube bt = (BodyTube) comp;
				String label = comp.getName()
						+ " (\u00d8"
						+ formatLength(bt.getInnerRadius() * 2.0)
						+ " x "
						+ formatLength(bt.getLength())
						+ ")";
				bodyTubes.add(new BodyTubeItem(bt, label));
			}
		}
	}

	/**
	 * Format a length value (in meters) for display.
	 */
	private String formatLength(double meters) {
		if (useMetric) {
			return String.format("%.1f mm", meters * 1000.0);
		} else {
			return String.format("%.2f in", meters / 0.0254);
		}
	}

	/**
	 * Build the dialog UI.
	 */
	private void buildUI() {
		JPanel panel = new JPanel(new MigLayout("fill, ins 10", "[right][grow,fill][right][grow,fill]"));

		// --- Tube selector ---
		if (!bodyTubes.isEmpty()) {
			panel.add(new JLabel("Select body tube:"), "span 1");
			tubeSelector = new JComboBox<>(bodyTubes.toArray(new BodyTubeItem[0]));
			tubeSelector.insertItemAt(new BodyTubeItem(null, "-- Manual entry --"), 0);
			tubeSelector.setSelectedIndex(1); // select first tube
			tubeSelector.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					onTubeSelected();
				}
			});
			panel.add(tubeSelector, "span 3, wrap");
		}

		// --- Diameter ---
		String diamLabel = useMetric ? "Inner diameter (mm):" : "Inner diameter (in):";
		panel.add(new JLabel(diamLabel));
		double defaultDiam = useMetric ? 50.0 : 2.0;
		double diamStep = useMetric ? 1.0 : 0.1;
		double diamMax = useMetric ? 500.0 : 20.0;
		diameterSpinner = new JSpinner(new SpinnerNumberModel(defaultDiam, 0.1, diamMax, diamStep));
		diameterSpinner.addChangeListener(e -> recalculate());
		panel.add(diameterSpinner, "wrap");

		// --- Length ---
		String lenLabel = useMetric ? "Compartment length (mm):" : "Compartment length (in):";
		panel.add(new JLabel(lenLabel));
		double defaultLen = useMetric ? 200.0 : 8.0;
		double lenStep = useMetric ? 10.0 : 0.5;
		double lenMax = useMetric ? 3000.0 : 120.0;
		lengthSpinner = new JSpinner(new SpinnerNumberModel(defaultLen, 0.1, lenMax, lenStep));
		lengthSpinner.addChangeListener(e -> recalculate());
		panel.add(lengthSpinner, "wrap");

		// --- Pressure slider ---
		panel.add(new JLabel("Target pressure (PSI):"));
		pressureSlider = new JSlider(SwingConstants.HORIZONTAL,
				(int) EjectionChargeCalculator.MIN_PRESSURE_PSI,
				(int) EjectionChargeCalculator.MAX_PRESSURE_PSI,
				(int) EjectionChargeCalculator.DEFAULT_PRESSURE_PSI);
		pressureSlider.setMajorTickSpacing(5);
		pressureSlider.setMinorTickSpacing(1);
		pressureSlider.setPaintTicks(true);
		pressureSlider.setPaintLabels(true);
		Hashtable<Integer, JLabel> sliderLabels = new Hashtable<>();
		sliderLabels.put(5, new JLabel("5"));
		sliderLabels.put(10, new JLabel("10"));
		sliderLabels.put(15, new JLabel("15"));
		sliderLabels.put(20, new JLabel("20"));
		pressureSlider.setLabelTable(sliderLabels);
		pressureSlider.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				recalculate();
			}
		});
		panel.add(pressureSlider);

		pressureValueLabel = new JLabel("15 PSI");
		panel.add(pressureValueLabel, "wrap para");

		// --- Results section ---
		JPanel resultsPanel = new JPanel(new MigLayout("fill, ins 5", "[right][grow,fill]"));
		resultsPanel.setBorder(BorderFactory.createTitledBorder("Results"));

		volumeLabel = new JLabel("-");
		resultsPanel.add(new JLabel("Compartment volume:"));
		resultsPanel.add(volumeLabel, "wrap");

		bpMassLabel = new JLabel("-");
		resultsPanel.add(new JLabel("BP charge (at target pressure):"));
		resultsPanel.add(bpMassLabel, "wrap");

		bpRangeLabel = new JLabel("-");
		resultsPanel.add(new JLabel("Recommended range (10-15 PSI):"));
		resultsPanel.add(bpRangeLabel, "wrap");

		bpOuncesLabel = new JLabel("-");
		resultsPanel.add(new JLabel("Equivalent in ounces:"));
		resultsPanel.add(bpOuncesLabel, "wrap");

		bpTeaspoonsLabel = new JLabel("-");
		resultsPanel.add(new JLabel("Approx. teaspoons FFFFg BP:"));
		resultsPanel.add(bpTeaspoonsLabel, "wrap");

		panel.add(resultsPanel, "span, growx, wrap para");

		// --- Safety warning ---
		JTextArea warningArea = new JTextArea(
				"WARNING: Always ground test your ejection charges before flight. "
						+ "Start with the minimum recommended charge and increase as needed. "
						+ "These calculations are approximate and do not account for air leaks, "
						+ "coupler fit, shear pins, or other factors. "
						+ "The user is solely responsible for safe testing and use."
		);
		warningArea.setLineWrap(true);
		warningArea.setWrapStyleWord(true);
		warningArea.setEditable(false);
		warningArea.setBackground(new Color(255, 255, 200));
		warningArea.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(new Color(200, 180, 0)),
				BorderFactory.createEmptyBorder(5, 5, 5, 5)
		));
		panel.add(warningArea, "span, growx, wrap para");

		// --- Unit toggle button ---
		JButton unitToggle = new JButton(useMetric ? "Switch to Imperial" : "Switch to Metric");
		unitToggle.addActionListener(e -> {
			useMetric = !useMetric;
			unitToggle.setText(useMetric ? "Switch to Imperial" : "Switch to Metric");
			// Rebuild the dialog
			getContentPane().removeAll();
			buildUI();
			recalculate();
			pack();
			revalidate();
			repaint();
		});
		panel.add(unitToggle, "span, split 2, left");

		// --- Close button ---
		JButton closeButton = new JButton("Close");
		closeButton.addActionListener(e -> dispose());
		panel.add(closeButton, "right, wrap");

		getContentPane().add(panel);

		// Pre-populate from first tube if available
		if (!bodyTubes.isEmpty()) {
			onTubeSelected();
		}
	}

	/**
	 * Handle selection of a body tube from the combo box.
	 */
	private void onTubeSelected() {
		if (tubeSelector == null) return;
		BodyTubeItem item = (BodyTubeItem) tubeSelector.getSelectedItem();
		if (item == null || item.tube == null) return;

		BodyTube bt = item.tube;
		double innerDiameter = bt.getInnerRadius() * 2.0;
		double length = bt.getLength();

		if (useMetric) {
			diameterSpinner.setValue(innerDiameter * 1000.0);  // m -> mm
			lengthSpinner.setValue(length * 1000.0);           // m -> mm
		} else {
			diameterSpinner.setValue(innerDiameter / 0.0254);  // m -> in
			lengthSpinner.setValue(length / 0.0254);           // m -> in
		}
	}

	/**
	 * Recalculate and update all output fields.
	 */
	private void recalculate() {
		double diamValue = ((Number) diameterSpinner.getValue()).doubleValue();
		double lenValue = ((Number) lengthSpinner.getValue()).doubleValue();
		int pressurePSI = pressureSlider.getValue();

		// Convert inputs to SI (meters)
		double diamM, lenM;
		if (useMetric) {
			diamM = diamValue / 1000.0;  // mm -> m
			lenM = lenValue / 1000.0;    // mm -> m
		} else {
			diamM = diamValue * 0.0254;  // in -> m
			lenM = lenValue * 0.0254;    // in -> m
		}

		double volumeM3 = EjectionChargeCalculator.calculateVolume(diamM, lenM);
		double pressurePa = EjectionChargeCalculator.psiToPascals(pressurePSI);

		double bpGrams = EjectionChargeCalculator.calculateBPMassGrams(pressurePa, volumeM3);
		double bpLow = EjectionChargeCalculator.calculateBPMassGrams(
				EjectionChargeCalculator.psiToPascals(EjectionChargeCalculator.LOW_PRESSURE_PSI), volumeM3);
		double bpHigh = EjectionChargeCalculator.calculateBPMassGrams(
				EjectionChargeCalculator.psiToPascals(EjectionChargeCalculator.HIGH_PRESSURE_PSI), volumeM3);

		double volumeCubicIn = EjectionChargeCalculator.cubicMetersToInches(volumeM3);
		double bpOunces = EjectionChargeCalculator.gramsToOunces(bpGrams);
		double bpTeaspoons = EjectionChargeCalculator.gramsToTeaspoons(bpGrams);

		// Update pressure label
		pressureValueLabel.setText(pressurePSI + " PSI ("
				+ String.format("%.0f", pressurePa / 1000.0) + " kPa)");

		// Update volume display
		if (useMetric) {
			double volumeCm3 = volumeM3 * 1e6;
			volumeLabel.setText(String.format("%.1f cm\u00b3 (%.2f in\u00b3)", volumeCm3, volumeCubicIn));
		} else {
			double volumeCm3 = volumeM3 * 1e6;
			volumeLabel.setText(String.format("%.2f in\u00b3 (%.1f cm\u00b3)", volumeCubicIn, volumeCm3));
		}

		// Update BP mass
		bpMassLabel.setText(String.format("%.2f g", bpGrams));
		bpRangeLabel.setText(String.format("%.2f g - %.2f g", bpLow, bpHigh));
		bpOuncesLabel.setText(String.format("%.3f oz", bpOunces));
		bpTeaspoonsLabel.setText(String.format("%.2f tsp", bpTeaspoons));
	}
}
