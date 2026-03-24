package ru.sbrf.uddk.ai.testing.lsprag.utils;

import com.intellij.icons.AllIcons;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class NotificationUtils {
    private static final String GROUP_ID = "LSPRAG Notifications";

    static {
        // Регистрируем группу уведомлений
        NotificationGroupManager.getInstance().getNotificationGroup(GROUP_ID);
    }

    /**
     * Показывает успешное уведомление
     */
    public static void showSuccess(@NotNull Project project, @NotNull String message) {
        Notification notification = new Notification(
                GROUP_ID,
                "LSPRAG",
                message,
                NotificationType.INFORMATION
        );
        notification.setIcon(AllIcons.General.InspectionsOK);
        Notifications.Bus.notify(notification, project);
    }

    /**
     * Показывает уведомление об ошибке
     */
    public static void showError(@NotNull Project project, @NotNull String message) {
        Notification notification = new Notification(
                GROUP_ID,
                "LSPRAG",
                message,
                NotificationType.ERROR
        );
        notification.setIcon(AllIcons.General.Error);
        Notifications.Bus.notify(notification, project);
    }

    /**
     * Показывает предупреждение
     */
    public static void showWarning(@NotNull Project project, @NotNull String message) {
        Notification notification = new Notification(
                GROUP_ID,
                "LSPRAG",
                message,
                NotificationType.WARNING
        );
        notification.setIcon(AllIcons.General.Warning);
        Notifications.Bus.notify(notification, project);
    }

    /**
     * Показывает уведомление с действием
     */
    public static void showWithAction(@NotNull Project project,
                                      @NotNull String message,
                                      @NotNull String actionText,
                                      @NotNull Runnable action) {
        Notification notification = new Notification(
                GROUP_ID,
                "LSPRAG",
                message,
                NotificationType.INFORMATION
        );

        notification.addAction(new NotificationAction(actionText) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent event, @NotNull Notification notification) {
                action.run();
                notification.expire();
            }
        });

        Notifications.Bus.notify(notification, project);
    }

    /**
     * Показывает уведомление о прогрессе
     */
    public static void showProgress(@NotNull Project project, @NotNull String message) {
        Notification notification = new Notification(
                GROUP_ID,
                "LSPRAG",
                message,
                NotificationType.INFORMATION
        );
        notification.setImportant(false);
        Notifications.Bus.notify(notification, project);
    }
}
