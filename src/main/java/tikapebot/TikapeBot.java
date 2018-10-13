package tikapebot;

import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendSticker;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

public class TikapeBot extends TelegramLongPollingBot {

    // Metodit liittyvät Telegramiin, joten arvostelun kannalta tämä ei ole oleellinen luokka
    private String previousMessage;

    @Override
    public String getBotUsername() {
        // TODO
        return "";
    }

    @Override
    public String getBotToken() {
        // TODO
        return "";
    }

    @Override
    public void onUpdateReceived(Update update) {
        Message message = update.getMessage();
        DatabaseAccess da = new DatabaseAccess("tieto.db");

        if (!message.hasSticker() && !message.hasText()) {
            return;
        }

        if (message.hasSticker()) {
            String fileId = message.getSticker().getFileId();
            String reply = "";

            // Viestissä ei saa olla muuta kuin tekstiä tai stickeri
            if (message.getReplyToMessage() != null && message.getReplyToMessage().hasSticker()) {
                return;
            } else if (message.isReply()) {
                reply = message.getReplyToMessage().getText();
            }

            if (reply.length() > 0) {
                try {
                    da.saveSticker(fileId, reply);
                } catch (SQLException ex) {
                    Logger.getLogger(TikapeBot.class.getName()).log(Level.SEVERE, null, ex);
                }
            } else if (this.previousMessage != null) {
                try {
                    da.saveSticker(fileId, this.previousMessage);
                } catch (SQLException ex) {
                    Logger.getLogger(TikapeBot.class.getName()).log(Level.SEVERE, null, ex);
                }
            }

        } else if (message.getText().equals("/sticker") || message.getText().equals("/sticker@TikapeBot")) {
            if (this.previousMessage != null) {
                String fileId = "";

                try {
                    fileId = da.getViableStickerFileId(this.previousMessage);
                } catch (SQLException ex) {
                    Logger.getLogger(TikapeBot.class.getName()).log(Level.SEVERE, null, ex);
                }

                if (fileId.length() > 0) {
                    sendMessage(fileId, update, true);
                }

            }
        } else if (message.getText().equals("/start") || message.getText().equals("/start@TikapeBot")) {
            sendMessage("Tervetuloa käyttämään TikapeBottia!\n\nTikapeBot oppii käyttäjien stickervalintoja siten, että "
                    + "se tallentaa sanan/lauseen ja sen jälkeen käytetyn stickerin tietokantaan. Sovellus ei tallenna käyttäjän tietoja (Chat id jne.), "
                    + "mutta jokainen TikapeBotille annettu sana tulee näkyville herokuappiin muiden nähtäväksi.\n\n"
                    + "/sticker - komento tulostaa stickerin",
                    update, false);
        } else if (message.hasText()) {
            try {
                da.updateOrSave(message.getText());
            } catch (SQLException ex) {
                Logger.getLogger(TikapeBot.class.getName()).log(Level.SEVERE, null, ex);
            }

            this.previousMessage = message.getText();
        }

    }

    public void sendMessage(String messageString, Update update, boolean isSticker) {

        if (!isSticker) {
            SendMessage message = new SendMessage();
            message.setChatId(update.getMessage().getChatId());
            message.setText(messageString);
            try {
                execute(message);
            } catch (TelegramApiException ex) {
                Logger.getLogger(TikapeBot.class.getName()).log(Level.SEVERE, null, ex);
            }
        } else {
            SendSticker stickers = new SendSticker();
            stickers.setChatId(update.getMessage().getChatId());
            stickers.setSticker(messageString);
            try {
                execute(stickers);
            } catch (TelegramApiException ex) {
                Logger.getLogger(TikapeBot.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
}
