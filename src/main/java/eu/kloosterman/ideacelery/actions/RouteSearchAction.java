package eu.kloosterman.ideacelery.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import eu.kloosterman.ideacelery.routing.RouteEntry;
import eu.kloosterman.ideacelery.routing.RouteIndexService;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Action that opens a custom search popup for discovering routes in the Celery framework.
 * Allows searching routes by URL or class name with keyboard-friendly navigation.
 */
public class RouteSearchAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }
        List<RouteEntry> allRoutes = RouteIndexService.getInstance(project).getRoutes();
        showSearchPopup(project, allRoutes);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(e.getProject() != null);
    }

    private void showSearchPopup(@NotNull Project project, @NotNull List<RouteEntry> allRoutes) {
        // Search field
        JBTextField searchField = new JBTextField();
        searchField.getEmptyText().setText("Search routes by URL or class name...");

        // Route list
        CollectionListModel<RouteEntry> listModel = new CollectionListModel<>(allRoutes);
        JBList<RouteEntry> list = new JBList<>(listModel);
        list.setCellRenderer(new RouteListCellRenderer());
        list.setVisibleRowCount(15);

        // Panel layout
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(searchField, BorderLayout.NORTH);
        panel.add(new JBScrollPane(list), BorderLayout.CENTER);
        panel.setPreferredSize(new Dimension(700, 400));

        // Popup
        JBPopup popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(panel, searchField)
            .setTitle("Celery Route Search")
            .setRequestFocus(true)
            .setFocusable(true)
            .setResizable(true)
            .setMovable(true)
            .setDimensionServiceKey(project, "CeleryRouteSearch", false)
            .createPopup();

        // Filter on typing
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                filterRoutes();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                filterRoutes();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                filterRoutes();
            }

            private void filterRoutes() {
                String query = searchField.getText();
                List<RouteEntry> filtered = allRoutes.stream()
                    .filter(r -> r.matches(query))
                    .collect(Collectors.toList());
                listModel.replaceAll(filtered);
                if (!filtered.isEmpty()) {
                    list.setSelectedIndex(0);
                }
            }
        });

        // Keyboard handling on search field
        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    RouteEntry selected = list.getSelectedValue();
                    if (selected != null) {
                        popup.cancel();
                        selected.navigate(project);
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    list.requestFocusInWindow();
                    if (list.getSelectedIndex() < 0 && listModel.getSize() > 0) {
                        list.setSelectedIndex(0);
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    popup.cancel();
                }
            }
        });

        // Keyboard handling on list
        list.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    RouteEntry selected = list.getSelectedValue();
                    if (selected != null) {
                        popup.cancel();
                        selected.navigate(project);
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_UP && list.getSelectedIndex() == 0) {
                    searchField.requestFocusInWindow();
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    popup.cancel();
                }
            }
        });

        // Double-click on list
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    RouteEntry selected = list.getSelectedValue();
                    if (selected != null) {
                        popup.cancel();
                        selected.navigate(project);
                    }
                }
            }
        });

        // Initial selection
        if (!allRoutes.isEmpty()) {
            list.setSelectedIndex(0);
        }

        // Show popup
        popup.showCenteredInCurrentWindow(project);
    }

    /**
     * Custom cell renderer for route entries in the search popup list.
     * Displays URL in bold, followed by class::method() in gray,
     * with a parse type indicator for non-HTML routes.
     */
    private static class RouteListCellRenderer extends ColoredListCellRenderer<RouteEntry> {
        @Override
        protected void customizeCellRenderer(
            @NotNull JList<? extends RouteEntry> list,
            RouteEntry value,
            int index,
            boolean selected,
            boolean hasFocus
        ) {
            if (value == null) {
                return;
            }
            append(value.getUrl(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
            append("  ", SimpleTextAttributes.REGULAR_ATTRIBUTES);
            append(value.getClassName(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
            append("::", SimpleTextAttributes.GRAYED_ATTRIBUTES);
            append(value.getMethodName() + "()", SimpleTextAttributes.GRAYED_ATTRIBUTES);
            if (!"html".equalsIgnoreCase(value.getParseType())) {
                append(" [" + value.getParseType().toUpperCase() + "]", SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES);
            }
        }
    }
}
