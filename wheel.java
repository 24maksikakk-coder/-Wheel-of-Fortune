// wheel.java
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

public class wheel {
    private static final String RESET = "\u001B[0m";
    private static final String RED = "\u001B[91m";
    private static final String GREEN = "\u001B[92m";
    private static final String YELLOW = "\u001B[93m";
    private static final String BLUE = "\u001B[94m";
    private static final String MAGENTA = "\u001B[95m";
    private static final String CYAN = "\u001B[96m";
    private static final String BOLD = "\u001B[1m";

    private static String colorize(String text, String color) {
        return color + text + RESET;
    }

    static class Sector {
        String name;
        String color;
        int value;
        boolean isBonus;
        boolean isBankrupt;
        boolean isGift;
        boolean isMystery;
    }

    static List<Sector> SECTORS = new ArrayList<>();
    static {
        String[][] data = {
            {"100", GREEN, "100"}, {"200", GREEN, "200"}, {"300", GREEN, "300"},
            {"500", GREEN, "500"}, {"800", GREEN, "800"}, {"1000", GREEN, "1000"},
            {"Бонус x2", MAGENTA, "bonus"}, {"Банкрот", RED, "bankrupt"},
            {"Подарок", CYAN, "gift"}, {"Загадка", YELLOW, "mystery"}
        };
        for (String[] d : data) {
            Sector s = new Sector();
            s.name = d[0];
            s.color = d[1];
            if (d[2].matches("\\d+")) {
                s.value = Integer.parseInt(d[2]);
            } else {
                s.value = 0;
                if (d[2].equals("bonus")) s.isBonus = true;
                else if (d[2].equals("bankrupt")) s.isBankrupt = true;
                else if (d[2].equals("gift")) s.isGift = true;
                else if (d[2].equals("mystery")) s.isMystery = true;
            }
            SECTORS.add(s);
        }
    }

    static class RecordData {
        int best_score;
    }

    private String mode;
    private String theme;
    private String word;
    private char[] displayWord;
    private Set<Character> guessed = new HashSet<>();
    private Set<Character> wrong = new HashSet<>();
    private int score;
    private int roundScore;
    private int attempts;
    private int maxAttempts = 6;
    private boolean gameOver;
    private String vowels = "аеёиоуыэюя";
    private String recordFile;
    private int bestScore;
    private int round;
    private int totalRounds;
    private Scanner scanner;

    public wheel(String mode, String theme, String word) {
        this.mode = mode;
        this.theme = theme;
        this.word = (word == null || word.isEmpty()) ? getRandomWord() : word;
        this.displayWord = this.word.toCharArray();
        for (int i=0; i<displayWord.length; i++) displayWord[i] = '_';
        this.score = 0;
        this.roundScore = 0;
        this.attempts = 0;
        this.gameOver = false;
        this.recordFile = System.getProperty("user.home") + "/.wheel_record.json";
        loadRecord();
        this.round = 1;
        this.totalRounds = mode.equals("classic") ? 1 : mode.equals("tournament") ? 3 : 1;
        this.scanner = new Scanner(System.in);
    }

    String getRandomWord() {
        Map<String, List<String>> words = new HashMap<>();
        words.put("животные", Arrays.asList("кот","собака","тигр","слон","жираф","дельфин"));
        words.put("растения", Arrays.asList("роза","тюльпан","кактус","папоротник","дуб"));
        words.put("страны", Arrays.asList("россия","германия","франция","италия","япония"));
        words.put("общее", Arrays.asList("программирование","алгоритм","компьютер","интернет","игра"));
        List<String> list = words.getOrDefault(theme, words.get("общее"));
        return list.get(new Random().nextInt(list.size()));
    }

    void loadRecord() {
        try {
            String json = new String(Files.readAllBytes(Paths.get(recordFile)));
            int idx = json.indexOf("\"best_score\"");
            if (idx != -1) {
                int start = json.indexOf(":", idx) + 1;
                int end = json.indexOf(",", start);
                if (end == -1) end = json.indexOf("}", start);
                bestScore = Integer.parseInt(json.substring(start, end).trim());
            } else bestScore = 0;
        } catch (Exception e) {
            bestScore = 0;
        }
    }

    void saveRecord() {
        try {
            Files.write(Paths.get(recordFile), ("{\"best_score\":" + bestScore + "}").getBytes());
        } catch (IOException e) {}
    }

    void displayState() {
        System.out.println(colorize("==================================================", BOLD));
        System.out.println(colorize("🎡 Колесо фортуны  |  Раунд " + round + "/" + totalRounds, BOLD));
        System.out.print(colorize("Слово: ", BLUE));
        for (char c : displayWord) System.out.print(c + " ");
        System.out.println();
        System.out.println(colorize("Тема: " + theme, CYAN));
        System.out.println(colorize("Очки: " + score + "  |  Очки раунда: " + roundScore, YELLOW));
        System.out.print(colorize("Открытые буквы: ", GREEN));
        if (guessed.isEmpty()) System.out.print("нет");
        else for (char c : guessed) System.out.print(c + " ");
        System.out.println();
        System.out.print(colorize("Ошибочные буквы: ", RED));
        if (wrong.isEmpty()) System.out.print("нет");
        else for (char c : wrong) System.out.print(c + " ");
        System.out.println();
        System.out.println(colorize("==================================================", BOLD));
    }

    Sector spinWheel() {
        System.out.println(colorize("🎡 Колесо вращается...", BOLD));
        Random rnd = new Random();
        for (int i=0; i<5; i++) {
            Sector s = SECTORS.get(rnd.nextInt(SECTORS.size()));
            System.out.print("\r" + colorize(s.name, s.color));
            try { Thread.sleep(200); } catch (InterruptedException e) {}
        }
        Sector s = SECTORS.get(rnd.nextInt(SECTORS.size()));
        System.out.print("\r" + colorize(s.name, s.color) + "\n");
        return s;
    }

    boolean buyVowel() {
        if (score < 100) {
            System.out.println(colorize("Недостаточно очков для покупки гласной (нужно 100).", RED));
            return false;
        }
        List<Character> vowelsList = new ArrayList<>();
        for (char v : vowels.toCharArray()) {
            if (!guessed.contains(v)) vowelsList.add(v);
        }
        if (vowelsList.isEmpty()) {
            System.out.println(colorize("Все гласные уже открыты.", YELLOW));
            return false;
        }
        char v = vowelsList.get(0);
        score -= 100;
        guessed.add(v);
        revealLetter(v);
        System.out.println(colorize("Вы купили гласную '" + v + "' за 100 очков.", GREEN));
        return true;
    }

    int revealLetter(char ch) {
        int count = 0;
        for (int i=0; i<word.length(); i++) {
            if (word.charAt(i) == ch) {
                displayWord[i] = ch;
                count++;
            }
        }
        return count;
    }

    boolean guessLetter(char ch) {
        if (guessed.contains(ch) || wrong.contains(ch)) {
            System.out.println(colorize("Вы уже называли эту букву.", YELLOW));
            return false;
        }
        if (vowels.indexOf(ch) != -1) {
            System.out.println(colorize("Гласные буквы можно только купить (команда buy).", YELLOW));
            return false;
        }
        if (word.indexOf(ch) != -1) {
            guessed.add(ch);
            int count = revealLetter(ch);
            roundScore += count * 50;
            System.out.println(colorize("Буква '" + ch + "' есть в слове! (" + count + " раз)", GREEN));
            if (new String(displayWord).indexOf('_') == -1) {
                winRound();
            }
            return true;
        } else {
            wrong.add(ch);
            attempts++;
            System.out.println(colorize("Буквы '" + ch + "' нет в слове.", RED));
            if (mode.equals("survival")) {
                roundScore = 0;
                System.out.println(colorize("Вы потеряли все очки раунда!", RED));
            }
            if (attempts >= maxAttempts) {
                System.out.println(colorize("Попытки закончились. Ход переходит к компьютеру.", YELLOW));
                gameOver = true;
            }
            return false;
        }
    }

    boolean guessWord(String guess) {
        if (guess.equals(word)) {
            winRound();
            return true;
        } else {
            attempts++;
            System.out.println(colorize("Неверно.", RED));
            if (mode.equals("survival")) {
                roundScore = 0;
                System.out.println(colorize("Вы потеряли все очки раунда!", RED));
            }
            if (attempts >= maxAttempts) gameOver = true;
            return false;
        }
    }

    void winRound() {
        System.out.println(colorize("🎉 Вы отгадали слово '" + word + "'!", GREEN));
        System.out.println(colorize("Вы заработали " + roundScore + " очков в этом раунде.", YELLOW));
        score += roundScore;
        if (mode.equals("classic") || round >= totalRounds) {
            gameOver = true;
        } else {
            round++;
            attempts = 0;
            roundScore = 0;
            word = getRandomWord();
            displayWord = word.toCharArray();
            for (int i=0; i<displayWord.length; i++) displayWord[i] = '_';
            guessed.clear();
            wrong.clear();
            System.out.println(colorize("Переход к раунду " + round, CYAN));
        }
    }

    void playTurn() {
        while (!gameOver) {
            displayState();
            System.out.println("\nДействия: spin, letter <буква>, guess <слово>, buy, quit");
            System.out.print("> ");
            String cmd = scanner.nextLine().trim();
            if (cmd.equals("quit")) { System.out.println("Выход."); return; }
            if (cmd.equals("spin")) {
                Sector sector = spinWheel();
                if (sector.value > 0) {
                    System.out.println(colorize("Выпало " + sector.value + " очков!", GREEN));
                    System.out.print("Введите букву: ");
                    String input = scanner.nextLine().trim();
                    if (input.length() != 1 || !Character.isLetter(input.charAt(0))) {
                        System.out.println(colorize("Введите одну букву.", RED));
                        continue;
                    }
                    char ch = Character.toLowerCase(input.charAt(0));
                    if (vowels.indexOf(ch) != -1) {
                        System.out.println(colorize("Гласные нужно покупать (команда buy).", YELLOW));
                        continue;
                    }
                    if (guessLetter(ch)) {
                        int count = word.length() - word.replace(String.valueOf(ch), "").length();
                        roundScore += count * sector.value;
                    } else {
                        System.out.println(colorize("Ошибка! Ход переходит к компьютеру.", RED));
                        gameOver = true;
                    }
                } else if (sector.isBonus) {
                    System.out.println(colorize("Бонус x2! Ваши очки удваиваются!", MAGENTA));
                    roundScore *= 2;
                } else if (sector.isBankrupt) {
                    System.out.println(colorize("Банкрот! Вы теряете все очки раунда!", RED));
                    roundScore = 0;
                } else if (sector.isGift) {
                    System.out.println(colorize("Подарок! Вы получаете дополнительный ход!", CYAN));
                    continue;
                } else if (sector.isMystery) {
                    int bonus = new Random().nextInt(251) + 50;
                    System.out.println(colorize("Загадка! Вы получаете " + bonus + " бонусных очков!", YELLOW));
                    roundScore += bonus;
                }
            } else if (cmd.startsWith("letter ")) {
                String[] parts = cmd.split(" ");
                if (parts.length < 2 || parts[1].length() != 1) {
                    System.out.println(colorize("Введите одну букву.", RED));
                    continue;
                }
                char ch = parts[1].toLowerCase().charAt(0);
                guessLetter(ch);
            } else if (cmd.startsWith("guess ")) {
                String guess = cmd.substring(6);
                guessWord(guess);
            } else if (cmd.equals("buy")) {
                if (mode.equals("classic")) buyVowel();
                else System.out.println(colorize("В этом режиме покупка гласных недоступна.", RED));
            } else {
                System.out.println(colorize("Неизвестная команда.", RED));
            }
        }
        if (gameOver && new String(displayWord).indexOf('_') == -1) {
            winRound();
        } else if (gameOver && new String(displayWord).indexOf('_') != -1) {
            System.out.println(colorize("Игра окончена. Загаданное слово: " + word, RED));
            System.out.println(colorize("Вы заработали " + score + " очков.", YELLOW));
        }
        if (score > bestScore) {
            bestScore = score;
            saveRecord();
            System.out.println(colorize("🏆 Новый рекорд: " + bestScore + " очков!", GREEN));
        }
        scanner.close();
    }

    public static void main(String[] args) {
        String mode = "classic", theme = "общее", word = "";
        boolean showStats = false, reset = false;
        for (int i=0; i<args.length; i++) {
            String arg = args[i];
            if (arg.equals("classic") || arg.equals("survival") || arg.equals("tournament")) mode = arg;
            else if (arg.equals("-w") && i+1 < args.length) word = args[++i];
            else if (arg.equals("-t") && i+1 < args.length) theme = args[++i];
            else if (arg.equals("-s") || arg.equals("--stats")) showStats = true;
            else if (arg.equals("-r") || arg.equals("--reset")) reset = true;
            else if (arg.equals("-h") || arg.equals("--help")) {
                System.out.println("Usage: java wheel [classic|survival|tournament] [-w word] [-t theme] [-s] [-r]");
                return;
            }
        }
        if (reset) {
            String f = System.getProperty("user.home") + "/.wheel_record.json";
            try { Files.deleteIfExists(Paths.get(f)); } catch (Exception e) {}
            System.out.println("Рекорды сброшены.");
            return;
        }
        if (showStats) {
            String f = System.getProperty("user.home") + "/.wheel_record.json";
            try {
                String json = new String(Files.readAllBytes(Paths.get(f)));
                int idx = json.indexOf("\"best_score\"");
                if (idx != -1) {
                    int start = json.indexOf(":", idx) + 1;
                    int end = json.indexOf(",", start);
                    if (end == -1) end = json.indexOf("}", start);
                    System.out.println("Лучший результат: " + Integer.parseInt(json.substring(start, end).trim()) + " очков");
                } else System.out.println("Рекордов пока нет.");
            } catch (Exception e) {
                System.out.println("Рекордов пока нет.");
            }
            return;
        }
        wheel game = new wheel(mode, theme, word);
        game.playTurn();
    }
}
