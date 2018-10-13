package tikapebot;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import spark.ModelAndView;
import spark.Spark;
import spark.template.thymeleaf.ThymeleafTemplateEngine;

public class Main {

    public static void main(String[] args) {

        if (System.getenv("PORT") != null) {
            Spark.port(Integer.valueOf(System.getenv("PORT")));
        }

        String[] fileIds = new String[1]; // Tallentaa käyttäjän hakeman stickerin id:n
        DatabaseAccess da = new DatabaseAccess("tieto.db");

        // << Telegram
        ApiContextInitializer.init();
        TelegramBotsApi telegramBotsApi = new TelegramBotsApi();
        TikapeBot bot = new TikapeBot();

        try {
            telegramBotsApi.registerBot(bot);

        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
        // Telegram >>

        Spark.get("/home", (req, res) -> {
            if (fileIds[0] == null) {
                fileIds[0] = " ";
            }
            List<String> list = new ArrayList<>();
            try {
                // Haetaan kaikki sanat tietokannasta listaan 'list'
                list = da.getAllWords();
            } catch (SQLException ex) {
                Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            }

            HashMap map = new HashMap<>();
            map.put("words", list);
            map.put("fileId", fileIds[0]);
            fileIds[0] = "";

            return new ModelAndView(map, "index");
        }, new ThymeleafTemplateEngine());

        Spark.get("/add", (req, res) -> {
            HashMap map = new HashMap<>();
            return new ModelAndView(map, "add");
        }, new ThymeleafTemplateEngine());

        // Käsittelee käyttäjän hakeman sanan
        Spark.post("/search", (req, res) -> {
            String word = req.queryParams("word");
            String fileId = da.getViableStickerFileId(word);
            fileIds[0] = fileId;
            res.redirect("/home");
            return "";
        });

        // Käsittelee sanan poistopyynnön
        Spark.post("/delete/:word", (req, res) -> {
            // Sana tulee muodossa: "sana : 1"
            String word = req.params("word");
            word = word.split(" ")[0];
            da.deleteWord(word);
            res.redirect("/home");
            return "";
        });

        // Käsittelee lisättävän sanan
        Spark.post("/addword", (req, res) -> {
            String word = req.queryParams("word");
            word = word.trim();
            if (word.length() > 0) {
                da.updateOrSave(word);
            }
            res.redirect("/add");
            return "";
        });

    }

}
