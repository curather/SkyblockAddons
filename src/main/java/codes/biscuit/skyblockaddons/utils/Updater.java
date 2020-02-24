package codes.biscuit.skyblockaddons.utils;

import codes.biscuit.skyblockaddons.SkyblockAddons;
import codes.biscuit.skyblockaddons.listeners.RenderListener;
import codes.biscuit.skyblockaddons.utils.nifty.ChatFormatting;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.common.ForgeVersion;
import org.apache.logging.log4j.Logger;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;

/**
 * This class handles update checking and downloading.
 */
public class Updater {
    private SkyblockAddons sba;
    @Getter
    private UpdateMeta updateMeta;
    private Logger logger;
    @Getter
    private Message message;

    private boolean shouldDrawMessage = false;

    public Updater(SkyblockAddons sba) {
        this.sba = sba;
        updateMeta = new UpdateMeta();
        this.logger = SkyblockAddons.getLogger();
    }

    /**
     * Called when the renderer finishes drawing the update message.
     */
    public void messageDrawn() {
        // Start the timer for resetting the message if it isn't one that needs to constantly update.
        if (!message.equals(Message.UPDATE_MESSAGE_DOWNLOAD)) {
            sba.getScheduler().schedule(Scheduler.CommandType.RESET_UPDATE_MESSAGE, 5);
        }
    }

    /**
     * Returns whether the update message should be rendered in a box on screen
     *
     * @see RenderListener#drawUpdateMessage()
     * @return {@code true} if {@code message} should be drawn, {@code false} if it shouldn't be drawn
     */
    public boolean shouldDrawMessage() {
        return shouldDrawMessage;
    }

    /**
     * Reads the results of the update check from the Forge Update Checker
     */
    public void parseUpdateCheckResults() {
        ForgeVersion.CheckResult checkResult = ForgeVersion.getResult(SkyblockAddons.getContainer());

        if (checkResult.status.equals(ForgeVersion.Status.OUTDATED)) {
            String[] currentVersion = SkyblockAddons.getContainer().getVersion().split(".");
            String[] newestVersion = checkResult.target.toString().split(".");

            // Is this a major update or a patch?
            if (newestVersion[0].compareTo(currentVersion[0]) > 0 || newestVersion[1].compareTo(currentVersion[1]) > 0) {
                message = Message.UPDATE_MESSAGE_MAJOR;
                shouldDrawMessage = true;
                sendUpdateMessage(true,false);
            }
            else {
                updateMeta.setPatch(true);
                message = Message.UPDATE_MESSAGE_PATCH;
                sendUpdateMessage(true,true);
            }
        }
        else if (checkResult.status.equals(ForgeVersion.Status.AHEAD) || checkResult.status.equals(ForgeVersion.Status.BETA)) {
            message = Message.MESSAGE_BETA_NOTICE;
            shouldDrawMessage = true;
        }
        else if (checkResult.status.equals(ForgeVersion.Status.BETA_OUTDATED)) {
            message = Message.UPDATE_MESSAGE_BETA;
            sendUpdateMessage(true, false);
        }
        else if (checkResult.status.equals(ForgeVersion.Status.PENDING)) {
            sba.getScheduler().schedule(Scheduler.CommandType.CHECK_UPDATE_RESULTS, 10);
        }
        else if (checkResult.status.equals(ForgeVersion.Status.FAILED)) {
            logger.error("Update check failed!");
        }
    }

    /**
     * Downloads a new release from the github to the mods folder
     *
     * @param version the version number of the release to download
     */
    public void downloadPatch(String version) {
        File sbaFolder = sba.getUtils().getSBAFolder();
        if (sbaFolder != null) {
            sba.getUtils().sendMessage(ChatFormatting.YELLOW+Message.UPDATE_MESSAGE_DOWNLOAD.getMessage());
            new Thread(() -> {
                try {
                    String fileName = "SkyblockAddons-"+version+"-for-MC-1.8.9.jar";
                    URL url = new URL("https://github.com/biscuut/SkyblockAddons/releases/download/v"+version+"/"+fileName);
                    File outputFile = new File(sbaFolder.toString()+File.separator+fileName);
                    URLConnection connection = url.openConnection();
                    long totalFileSize = connection.getContentLengthLong();
                    updateMeta.setTotalBytes(totalFileSize);
                    updateMeta.setOutputFileName(fileName);
                    message = Message.UPDATE_MESSAGE_DOWNLOAD;
                    shouldDrawMessage = true;
                    connection.setConnectTimeout(5000);
                    connection.setReadTimeout(5000);
                    BufferedInputStream inputStream = new BufferedInputStream(connection.getInputStream());
                    FileOutputStream fileOutputStream = new FileOutputStream(outputFile);
                    byte[] dataBuffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(dataBuffer, 0, 1024)) != -1) {
                        fileOutputStream.write(dataBuffer, 0, bytesRead);
                        updateMeta.setDownloadedBytes(updateMeta.getDownloadedBytes()+bytesRead);
                    }
                    message = Message.UPDATE_MESSAGE_DOWNLOAD_FINISHED;
                } catch (IOException e) {
                    message = Message.UPDATE_MESSAGE_FAILED;
                    shouldDrawMessage = true;
                    logger.error("Download patch failed!", e);
                }
            }).start();
        }
    }

    /**
     * Sends a chat notification when a new update is available
     *
     * @param showDownload Should the update's download link be shown?
     * @param showAutoDownload Should a button for downloading the update automatically be shown?
     */
    void sendUpdateMessage(boolean showDownload, boolean showAutoDownload) {
        String newestVersion = updateMeta.getNewestVersion();

        sba.getUtils().sendMessage(Utils.MULTILINE_MESSAGE_HEADER, false);
        if (message.equals(Message.UPDATE_MESSAGE_DOWNLOAD_FINISHED)) {
            ChatComponentText deleteOldFile = new ChatComponentText(ChatFormatting.RED+Message.UPDATE_MESSAGE_DOWNLOAD_FINISHED.getMessage()+"\n");
            sba.getUtils().sendMessage(deleteOldFile, false);
        } else {
            ChatComponentText newUpdate = new ChatComponentText(ChatFormatting.AQUA+Message.MESSAGE_NEW_UPDATE.getMessage(newestVersion)+"\n");
            sba.getUtils().sendMessage(newUpdate, false);
        }

        ChatComponentText buttonsMessage = new ChatComponentText("");
        if (showDownload) {
            buttonsMessage = new ChatComponentText(ChatFormatting.AQUA.toString() + ChatFormatting.BOLD + '[' + Message.MESSAGE_DOWNLOAD_LINK.getMessage(newestVersion) + ']');
            buttonsMessage.setChatStyle(buttonsMessage.getChatStyle().setChatClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, updateMeta.getDownloadLink())));
            buttonsMessage.appendSibling(new ChatComponentText(" "));
        }

        if (showAutoDownload) {
            ChatComponentText downloadAutomatically = new ChatComponentText(ChatFormatting.GREEN.toString() + ChatFormatting.BOLD + '[' + Message.MESSAGE_DOWNLOAD_AUTOMATICALLY.getMessage(newestVersion) + ']');
            downloadAutomatically.setChatStyle(downloadAutomatically.getChatStyle().setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sba update")));
            buttonsMessage.appendSibling(downloadAutomatically);
            buttonsMessage.appendSibling(new ChatComponentText(" "));
        }

        ChatComponentText openModsFolder = new ChatComponentText(ChatFormatting.YELLOW.toString() + ChatFormatting.BOLD + '[' + Message.MESSAGE_OPEN_MODS_FOLDER.getMessage(newestVersion) + ']');
        openModsFolder.setChatStyle(openModsFolder.getChatStyle().setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sba folder")));
        buttonsMessage.appendSibling(openModsFolder);

        sba.getUtils().sendMessage(buttonsMessage, false);
        
        // Add a button to view the patch notes in the Discord.
        if (!message.equals(Message.UPDATE_MESSAGE_DOWNLOAD_FINISHED)) {
            ChatComponentText discord = new ChatComponentText(ChatFormatting.AQUA + Message.MESSAGE_VIEW_PATCH_NOTES.getMessage() + " " +
                    ChatFormatting.BLUE.toString() + ChatFormatting.BOLD + '[' + Message.MESSAGE_JOIN_DISCORD.getMessage() + ']');
            discord.setChatStyle(discord.getChatStyle().setChatClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, EnumUtils.Social.DISCORD.getUrl().toString())));
            sba.getUtils().sendMessage(discord);
        }
        sba.getUtils().sendMessage(Utils.MULTILINE_MESSAGE_FOOTER, false);
    }

    /**
     * Clears the update message
     */
    void resetMessage() {
        message = null;
        shouldDrawMessage = false;
    }

    /*
     * This class stores the metadata for the current update.
     */
    @Getter @Setter
    public class UpdateMeta {
        private boolean patch = false;
        private long downloadedBytes = 0;
        private long totalBytes = 0;
        private String newestVersion = "";
        private String outputFileName = "";
        private String downloadLink = "";
    }
}
