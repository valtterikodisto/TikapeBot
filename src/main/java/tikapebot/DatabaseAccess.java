package tikapebot;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class DatabaseAccess {

    private String database;

    public DatabaseAccess(String databaseFileName) {
        this.database = databaseFileName;
    }

    // Palauttaa stickerin id:n tietokannassa kokonaislukuna
    public int getStickerId(String fileId) throws SQLException {
        Connection conn = getConnetion();
        PreparedStatement stmt = conn.prepareStatement("SELECT id FROM Sticker WHERE fileid = ?");
        stmt.setString(1, fileId);

        ResultSet rs = stmt.executeQuery();

        int id = -1;
        while (rs.next()) {
            id = rs.getInt("id");
        }

        stmt.close();
        conn.close();

        return id;
    }

    // Jos sanaan ei löydy sopivaa stickeriä
    public String getBackupStickerId() {
        return "CAADBAAD1wADW1XTBrheHAqywvQgAg";
    }

    // Palauttaa sanan id:n tietokannassa
    public int getWordId(String word) throws SQLException {
        Connection conn = getConnetion();
        PreparedStatement stmt = conn.prepareStatement("SELECT id FROM Word WHERE name = ?");
        stmt.setString(1, word);

        ResultSet rs = stmt.executeQuery();

        int id = -1;
        while (rs.next()) {
            id = rs.getInt("id");
        }

        stmt.close();
        conn.close();

        return id;
    }

    // Tallentaa stickerin ja siihen liittyvän tekstin
    // Metodi pilkkoo tekstin sanoiksi ja liittää jokaiseen viestissä esiintyneeseen sanaan stickerin
    public void saveSticker(String fileId, String message) throws SQLException {
        String[] split = message.split(" ");
        Connection conn = getConnetion();

        for (String word : split) {
            word = word.toLowerCase();

            if (wordIsInvalid(word)) {
                continue;
            }

            int wordId = getWordId(word);
            int stickerId = getStickerId(fileId);

            if (getStickerId(fileId) < 0) {
                PreparedStatement stmt = conn.prepareStatement("INSERT INTO Sticker(fileid) VALUES(?)");
                stmt.setString(1, fileId);
                stmt.executeUpdate();
                stmt.close();
                stickerId = getStickerId(fileId);
            }

            int wordStickerAmount = getWordStickerAmount(wordId, stickerId);
            if (wordStickerAmount < 0) {
                PreparedStatement stmt = conn.prepareStatement("INSERT INTO WordSticker(word_id, sticker_id, amount) VALUES(?,?,?)");
                stmt.setInt(1, wordId);
                stmt.setInt(2, stickerId);
                stmt.setInt(3, 0);
                stmt.executeUpdate();
                stmt.close();
                wordStickerAmount = 0;
            }

            PreparedStatement stmt = conn.prepareStatement("UPDATE WordSticker SET amount=? WHERE word_id=? AND sticker_id=?");
            stmt.setInt(1, wordStickerAmount + 1);
            stmt.setInt(2, wordId);
            stmt.setInt(3, stickerId);
            stmt.executeUpdate();
            stmt.close();
        }

        conn.close();

    }

    // Palauttaa montako kertaa tiettyyn sanaan on liitetty stickeri
    public int getWordStickerAmount(int wordId, int stickerId) throws SQLException {
        Connection conn = getConnetion();
        PreparedStatement stmt = conn.prepareStatement("SELECT amount FROM WordSticker WHERE word_id=? AND sticker_id=?");
        stmt.setInt(1, wordId);
        stmt.setInt(2, stickerId);
        ResultSet rs = stmt.executeQuery();

        int amount = -1;
        while (rs.next()) {
            amount = rs.getInt("amount");
        }

        return amount;
    }

    // Tallennetaan saatu viesti tietokantaan
    public void updateOrSave(String message) throws SQLException {
        String[] split = message.split(" ");

        if (split[0].startsWith("/")) {
            return;
        }

        Connection conn = getConnetion();
        for (String word : split) {
            word = word.toLowerCase();
            PreparedStatement stmt = conn.prepareStatement("SELECT id, amount FROM Word WHERE name = ?");
            stmt.setString(1, word);
            ResultSet rs = stmt.executeQuery();

            int id = -1;
            int amount = -1;
            while (rs.next()) {
                id = rs.getInt("id");
                amount = rs.getInt("amount");
            }

            if (id < 0) {
                stmt.close();
                stmt = conn.prepareStatement("INSERT INTO Word(name, amount) VALUES (?,?)");
                stmt.setString(1, word);
                stmt.setInt(2, 1);
                stmt.executeUpdate();
                stmt.close();
            } else {
                stmt.close();
                stmt = conn.prepareStatement("UPDATE Word SET amount=? WHERE id=?");
                stmt.setInt(1, amount + 1);
                stmt.setInt(2, id);
                stmt.executeUpdate();
                stmt.close();
            }
        }

        conn.close();
    }

    // Palautetaan stickerin tiedosto id perustuen viestin sisältöön
    public String getViableStickerFileId(String message) throws SQLException {
        String[] split = message.split(" ");
        HashMap<Integer, Integer> stickerCounter = new HashMap<>();

        Connection conn = getConnetion();
        for (int i = 0; i < split.length; i++) {
            String word = split[i];

            if (wordIsInvalid(word)) {
                continue;
            }

            PreparedStatement stmt = conn.prepareStatement("SELECT WordSticker.sticker_id AS stickerId, WordSticker.amount AS stickerAmount "
                    + "FROM Word "
                    + "INNER JOIN WordSticker ON WordSticker.word_id = Word.id "
                    + "WHERE Word.name = ?");
            stmt.setString(1, word);

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                int stickerId = rs.getInt("stickerId");

                stickerCounter.putIfAbsent(stickerId, 0);
                stickerCounter.put(stickerId, (stickerCounter.get(stickerId) + rs.getInt("stickerAmount")));
            }

            rs.close();
            stmt.close();
        }

        int maxValue = -1;
        int maxValueId = -1;

        for (int id : stickerCounter.keySet()) {
            if (stickerCounter.get(id) >= maxValue) {
                maxValue = stickerCounter.get(id);
                maxValueId = id;
            }
        }

        PreparedStatement stmt = conn.prepareStatement("SELECT fileid FROM Sticker WHERE id = ?");
        stmt.setInt(1, maxValueId);
        ResultSet rs = stmt.executeQuery();

        String fileId = "";
        while (rs.next()) {
            fileId = rs.getString("fileid");
        }

        if (fileId.isEmpty()) {
            fileId = getBackupStickerId();
        }

        conn.close();

        return fileId;
    }

    // Mikäli sana on yli 40 merkkiä pitkä, sitä ei tallenneta tietokantaan
    public boolean wordIsInvalid(String word) {
        if (word.length() > 40) {
            return true;
        }
        return false;
    }

    // Palauttaa kaikki tietokannassa olevat sanat listana
    public List<String> getAllWords() throws SQLException {
        Connection conn = getConnetion();
        PreparedStatement stmt = conn.prepareStatement("SELECT name, amount FROM Word");
        ResultSet rs = stmt.executeQuery();
        List<String> list = new ArrayList<>();
        while (rs.next()) {
            list.add(rs.getString("name") + " : " + rs.getInt("amount"));
        }
        rs.close();
        stmt.close();
        conn.close();
        return list;
    }

    // Poistaa tietokannasta sanan
    public void deleteWord(String word) throws SQLException {
        Connection conn = getConnetion();
        PreparedStatement stmt = conn.prepareStatement("DELETE FROM WordSticker WHERE word_id = ?");
        int wordId = getWordId(word.trim());
        stmt.setInt(1, wordId);
        stmt.executeUpdate();
        stmt = conn.prepareStatement("DELETE FROM Word WHERE id = ?");
        stmt.setInt(1, wordId);
        stmt.executeUpdate();

        stmt.close();
        conn.close();
    }

    // Palauttaa tietokannan yhteyden
    public Connection getConnetion() throws SQLException {
        String dbUrl = System.getenv("JDBC_DATABASE_URL");
        if (dbUrl != null && dbUrl.length() > 0) {
            return DriverManager.getConnection(dbUrl);
        }
        return DriverManager.getConnection("jdbc:sqlite:" + this.database);
    }

}
