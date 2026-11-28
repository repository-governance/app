package org.cda.archi.githubworkspace.views;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.UUID;
import java.util.function.Consumer;

import org.cda.archi.githubworkspace.models.ColumnDefinition;
import org.cda.archi.githubworkspace.models.DataType;
import org.cda.archi.githubworkspace.models.TableSchema;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DateTime;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

/** Interprets a TableSchema to render and validate native SWT editors at runtime. */
public final class DynamicFormRenderer extends Composite {
    private final TableSchema schema;
    private final boolean editing;
    private final Map<String, Control> editors = new LinkedHashMap<>();
    private final Map<String, Supplier<String>> valueReaders = new LinkedHashMap<>();
    private final Map<String, Label> errors = new LinkedHashMap<>();
    private final Consumer<Boolean> validityChanged;
    private Composite formContents;
    private ScrolledComposite scroller;

    public DynamicFormRenderer(Composite parent, TableSchema schema, Map<String, Object> values,
            boolean editing, Consumer<Boolean> validityChanged) {
        super(parent, SWT.NONE);
        this.schema = schema;
        this.editing = editing;
        this.validityChanged = validityChanged == null ? ignored -> { } : validityChanged;
        setLayout(new GridLayout(2, false));
        scroller = new ScrolledComposite(this, SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
        scroller.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1));
        scroller.setExpandHorizontal(true);
        scroller.setExpandVertical(true);
        formContents = new Composite(scroller, SWT.NONE);
        formContents.setLayout(new GridLayout(1, false));
        scroller.setContent(formContents);
        Group form = new Group(formContents, SWT.NONE);
        form.setText(schema.name());
        form.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        form.setLayout(new GridLayout(2, false));
        for (ColumnDefinition column : schema.columns()) {
            Label label = new Label(form, SWT.NONE);
            label.setText(column.name() + (column.required() ? " *" : ""));
            label.setToolTipText(column.type().name());
            Object value = values == null ? null : values.get(column.name());
            Control editor = createEditor(form, column, value);
            editor.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
            editors.put(column.name(), editor);
            valueReaders.putIfAbsent(column.name(), () -> readValue(editor));
            Label error = new Label(form, SWT.WRAP);
            error.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
            errors.put(column.name(), error);
        }
        scroller.setMinSize(formContents.computeSize(SWT.DEFAULT, SWT.DEFAULT));
        validate();
    }

    public Map<String, String> values() {
        Map<String, String> result = new LinkedHashMap<>();
        for (ColumnDefinition column : schema.columns()) {
            result.put(column.name(), valueReaders.get(column.name()).get());
        }
        return result;
    }

    public boolean validate() {
        boolean valid = true;
        Map<String, String> raw = values();
        for (ColumnDefinition column : schema.columns()) {
            String value = raw.get(column.name());
            String problem = validateValue(column, value);
            Label label = errors.get(column.name());
            label.setText(problem == null ? "" : problem);
            valid &= problem == null;
        }
        layout(true, true);
        if (scroller != null && !scroller.isDisposed()) {
            scroller.setMinSize(formContents.computeSize(SWT.DEFAULT, SWT.DEFAULT));
        }
        validityChanged.accept(valid);
        return valid;
    }

    private Control createEditor(Composite parent, ColumnDefinition column, Object value) {
        if (column.type() == DataType.BOOLEAN) {
            Button check = new Button(parent, SWT.CHECK);
            check.setText("Enabled");
            check.setGrayed(value == null);
            check.setSelection(Boolean.TRUE.equals(value));
            check.setEnabled(!(editing && column.primaryKey()));
            check.addListener(SWT.Selection, event -> { check.setGrayed(false); validate(); });
            valueReaders.put(column.name(),
                    () -> check.getGrayed() ? "" : Boolean.toString(check.getSelection()));
            return check;
        }
        if (column.type() == DataType.DATE || column.type() == DataType.DATETIME) {
            Composite holder = new Composite(parent, SWT.NONE);
            holder.setLayout(new GridLayout(2, false));
            Button set = new Button(holder, SWT.CHECK);
            set.setText("Set");
            set.setSelection(value != null);
            int dateStyle = SWT.DATE | SWT.DROP_DOWN;
            if (column.type() == DataType.DATETIME) dateStyle |= SWT.TIME;
            DateTime date = new DateTime(holder, dateStyle);
            date.setEnabled(value != null);
            if (value != null) {
                try {
                    if (column.type() == DataType.DATE) {
                        LocalDate parsed = LocalDate.parse(String.valueOf(value));
                        date.setDate(parsed.getYear(), parsed.getMonthValue() - 1, parsed.getDayOfMonth());
                    } else {
                        OffsetDateTime parsed = OffsetDateTime.parse(String.valueOf(value))
                                .withOffsetSameInstant(java.time.ZoneOffset.UTC);
                        date.setDate(parsed.getYear(), parsed.getMonthValue() - 1, parsed.getDayOfMonth());
                        date.setTime(parsed.getHour(), parsed.getMinute(), parsed.getSecond());
                    }
                } catch (RuntimeException ignored) { }
            }
            Button setValue = set;
            setValue.addListener(SWT.Selection, event -> {
                date.setEnabled(setValue.getSelection());
                validate();
            });
            valueReaders.put(column.name(), () -> {
                if (!setValue.getSelection()) return "";
                String day = String.format(java.util.Locale.ROOT, "%04d-%02d-%02d",
                        date.getYear(), date.getMonth() + 1, date.getDay());
                if (column.type() == DataType.DATE) return day;
                LocalDateTime local = LocalDateTime.of(date.getYear(), date.getMonth() + 1, date.getDay(),
                        date.getHours(), date.getMinutes(), date.getSeconds());
                java.time.ZoneOffset offset = java.time.ZoneId.systemDefault().getRules().getOffset(local);
                return OffsetDateTime.of(local, offset).toString();
            });
            date.addListener(SWT.Selection, event -> validate());
            return holder;
        }
        int style = SWT.BORDER;
        if (column.type() == DataType.TEXT) style |= SWT.MULTI | SWT.WRAP | SWT.V_SCROLL;
        if (column.primaryKey() && editing) style |= SWT.READ_ONLY;
        Text text = new Text(parent, style);
        text.setMessage(message(column.type()));
        text.setText(value == null ? (column.type() == DataType.UUID && !editing && column.primaryKey()
                ? UUID.randomUUID().toString() : "") : String.valueOf(value));
        text.addModifyListener(event -> validate());
        if (column.type() == DataType.TEXT) {
            GridData textData = new GridData(SWT.FILL, SWT.FILL, true, false);
            textData.heightHint = 72;
            text.setLayoutData(textData);
        }
        if (column.type() == DataType.UUID && !editing && column.primaryKey()) {
            Composite holder = new Composite(parent, SWT.NONE);
            // Text is moved into the holder to keep the generator adjacent without non-native controls.
            text.setParent(holder);
            holder.setLayout(new GridLayout(2, false));
            text.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
            Button generate = new Button(holder, SWT.PUSH);
            generate.setText("Generate");
            generate.addListener(SWT.Selection, event -> text.setText(UUID.randomUUID().toString()));
            // The holder is the actual cell control; keep the Text for value extraction.
            valueReaders.put(column.name(), text::getText);
            return holder;
        }
        return text;
    }

    private static String readValue(Control control) {
        if (control instanceof Text text) return text.getText();
        if (control instanceof Button button) {
            return button.getGrayed() ? "" : Boolean.toString(button.getSelection());
        }
        return "";
    }

    private static String message(DataType type) {
        return switch (type) {
            case INTEGER -> "Whole number";
            case DECIMAL -> "Decimal number";
            case DATE -> "YYYY-MM-DD";
            case DATETIME -> "YYYY-MM-DDThh:mm:ss±hh:mm";
            case EMAIL -> "name@example.com";
            case URL -> "https://";
            case UUID -> "UUID";
            default -> "";
        };
    }

    private static String validateValue(ColumnDefinition column, String raw) {
        if (raw == null || raw.isBlank()) {
            return column.required() ? "Required" : null;
        }
        String text = raw.trim();
        try {
            switch (column.type()) {
                case INTEGER -> Integer.parseInt(text);
                case DECIMAL -> new BigDecimal(text);
                case BOOLEAN -> {
                    if (!"true".equalsIgnoreCase(text) && !"false".equalsIgnoreCase(text)) return "Enter true or false.";
                }
                case DATE -> LocalDate.parse(text);
                case DATETIME -> OffsetDateTime.parse(text);
                case EMAIL -> {
                    if (!text.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) return "Enter a valid email address.";
                }
                case URL -> {
                    java.net.URI uri = java.net.URI.create(text);
                    if (uri.getHost() == null || !("http".equalsIgnoreCase(uri.getScheme())
                            || "https".equalsIgnoreCase(uri.getScheme()))) return "Enter an HTTP(S) URL.";
                }
                case UUID -> UUID.fromString(text);
                case STRING -> {
                    if (!column.matchesPattern(raw)) return column.patternFailureMessage();
                }
                case TEXT -> { }
            }
            return null;
        } catch (RuntimeException ex) {
            return "Invalid " + column.type().name() + " value.";
        }
    }
}
