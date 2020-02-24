package codes.biscuit.skyblockaddons.utils;

import codes.biscuit.skyblockaddons.SkyblockAddons;
import codes.biscuit.skyblockaddons.utils.nifty.ChatFormatting;
import lombok.Getter;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.common.ForgeVersion;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;

/**
 * This class
 */
public class Updater {
    private SkyblockAddons sba;
    @Getter
    private DownloadInfo downloadInfo;

    public Updater(SkyblockAddons sba) {
        this.sba = sba;
        downloadInfo = new DownloadInfo(sba);
    }

    public void parseUpdateCheckResults() {
        ForgeVersion.CheckResult checkResult = ForgeVersion.getResult(SkyblockAddons.getContainer());

        if (checkResult.status.equals(ForgeVersion.Status.OUTDATED)) {
            String[] currentVersion = SkyblockAddons.getContainer().getVersion().split(".");
            String[] newestVersion = checkResult.target.toString().split(".");

            // Is this a major update or a patch?
            if (newestVersion[0].compareTo(currentVersion[0]) > 0 || newestVersion[1].compareTo(currentVersion[1]) > 0) {
                downloadInfo.setMessageType(EnumUtils.UpdateMessageType.MAJOR_AVAILABLE);
                sendUpdateMessage(true,false);
            }
            else {
                downloadInfo.setPatch(true);
                downloadInfo.setMessageType(EnumUtils.UpdateMessageType.PATCH_AVAILABLE);
                sendUpdateMessage(true,true);
            }
        }
        else if (checkResult.status.equals(ForgeVersion.Status.AHEAD) || checkResult.status.equals(ForgeVersion.Status.BETA)) {
            downloadInfo.setMessageType(EnumUtils.UpdateMessageType.DEVELOPMENT);
        }
        else if (checkResult.status.equals(ForgeVersion.Status.BETA_OUTDATED)) {
            downloadInfo.setMessageType(EnumUtils.UpdateMessageType.DEVELOPMENT);
        }
        else if (checkResult.status.equals(ForgeVersion.Status.PENDING)) {
            sba.getScheduler().schedule(Scheduler.CommandType.CHECK_UPDATE_RESULTS, 10);
        }
        else if (checkResult.status.equals(ForgeVersion.Status.FAILED)) {
            SkyblockAddons.getLogger().error("Update check failed!");
        }
    }

    public void downloadPatch(String version) {
        File sbaFolder = sba.getUtils().getSBAFolder();
        if (sbaFolder != null) {
            sba.getUtils().sendMessage(ChatFormatting.YELLOW+Message.MESSAGE_DOWNLOADING_UPDATE.getMessage());
            new Thread(() -> {
                try {
                    String fileName = "SkyblockAddons-"+version+"-for-MC-1.8.9.jar";
                    URL url = new URL("https://github.com/biscuut/SkyblockAddons/releases/download/v"+version+"/"+fileName);
                    File outputFile = new File(sbaFolder.toString()+File.separator+fileName);
                    URLConnection connection = url.openConnection();
                    long totalFileSize = connection.getContentLengthLong();
                    downloadInfo.setTotalBytes(totalFileSize);
                    downloadInfo.setOutputFileName(fileName);
                    downloadInfo.setMessageType(EnumUtils.UpdateMessageType.DOWNLOADING);
                    connection.setConnectTimeout(5000);
                    connection.setReadTimeout(5000);
                    BufferedInputStream inputStream = new BufferedInputStream(connection.getInputStream());
                    FileOutputStream fileOutputStream = new FileOutputStream(outputFile);
                    byte[] dataBuffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(dataBuffer, 0, 1024)) != -1) {
                        fileOutputStream.write(dataBuffer, 0, bytesRead);
                        downloadInfo.setDownloadedBytes(downloadInfo.getDownloadedBytes()+bytesRead);
                    }
                    downloadInfo.setMessageType(EnumUtils.UpdateMessageType.DOWNLOAD_FINISHED);
                } catch (IOException e) {
                    downloadInfo.setMessageType(EnumUtils.UpdateMessageType.FAILED);
                    e.printStackTrace();
                }
            }).start();
        }
    }

    void sendUpdateMessage(boolean showDownload, boolean showAutoDownload) {
        String newestVersion = downloadInfo.getNewestVersion();

        sba.getUtils().sendMessage(Utils.MULTILINE_MESSAGE_HEADER, false);
        if (downloadInfo.getMessageType() == EnumUtils.UpdateMessageType.DOWNLOAD_FINISHED) {
            ChatComponentText deleteOldFile = new ChatComponentText(ChatFormatting.RED+Message.MESSAGE_DELETE_OLD_FILE.getMessage()+"\n");
            sba.getUtils().sendMessage(deleteOldFile, false);
        } else {
            ChatComponentText newUpdate = new ChatComponentText(ChatFormatting.AQUA+Message.MESSAGE_NEW_UPDATE.getMessage(newestVersion)+"\n");
            sba.getUtils().sendMessage(newUpdate, false);
        }

        ChatComponentText buttonsMessage = new ChatComponentText("");
        if (showDownload) {
            buttonsMessage = new ChatComponentText(ChatFormatting.AQUA.toString() + ChatFormatting.BOLD + '[' + Message.MESSAGE_DOWNLOAD_LINK.getMessage(newestVersion) + ']');
            buttonsMessage.setChatStyle(buttonsMessage.getChatStyle().setChatClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, downloadInfo.getDownloadLink())));
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
        if (downloadInfo.getMessageType() != EnumUtils.UpdateMessageType.DOWNLOAD_FINISHED) {
            ChatComponentText discord = new ChatComponentText(ChatFormatting.AQUA + Message.MESSAGE_VIEW_PATCH_NOTES.getMessage() + " " +
                    ChatFormatting.BLUE.toString() + ChatFormatting.BOLD + '[' + Message.MESSAGE_JOIN_DISCORD.getMessage() + ']');
            discord.setChatStyle(discord.getChatStyle().setChatClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, EnumUtils.Social.DISCORD.getUrl().toString())));
            sba.getUtils().sendMessage(discord);
        }
        sba.getUtils().sendMessage(Utils.MULTILINE_MESSAGE_FOOTER, false);
    }
}
