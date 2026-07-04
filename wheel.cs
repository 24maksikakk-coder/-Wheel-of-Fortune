// wheel.cs
using System;
using System.Collections.Generic;
using System.IO;
using System.Text.Json;
using System.Linq;
using System.Threading;

class WheelOfFortune
{
    static string Colorize(string text, string color)
    {
        string col = color switch
        {
            "red" => "\x1b[91m",
            "green" => "\x1b[92m",
            "yellow" => "\x1b[93m",
            "blue" => "\x1b[94m",
            "magenta" => "\x1b[95m",
            "cyan" => "\x1b[96m",
            "bold" => "\x1b[1m",
            _ => "\x1b[0m"
        };
        return col + text + "\x1b[0m";
    }

    class Sector
    {
        public string Name { get; set; }
        public string Color { get; set; }
        public int Value { get; set; }
        public bool IsBonus { get; set; }
        public bool IsBankrupt { get; set; }
        public bool IsGift { get; set; }
        public bool IsMystery { get; set; }
    }

    static List<Sector> SECTORS = new List<Sector>
    {
        new Sector { Name="100", Color="green", Value=100 },
        new Sector { Name="200", Color="green", Value=200 },
        new Sector { Name="300", Color="green", Value=300 },
        new Sector { Name="500", Color="green", Value=500 },
        new Sector { Name="800", Color="green", Value=800 },
        new Sector { Name="1000", Color="green", Value=1000 },
        new Sector { Name="Бонус x2", Color="magenta", IsBonus=true },
        new Sector { Name="Банкрот", Color="red", IsBankrupt=true },
        new Sector { Name="Подарок", Color="cyan", IsGift=true },
        new Sector { Name="Загадка", Color="yellow", IsMystery=true }
    };

    class RecordData
    {
        public int best_score { get; set; }
    }

    private string mode;
    private string theme;
    private string word;
    private char[] displayWord;
    private HashSet<char> guessedLetters = new HashSet<char>();
    private HashSet<char> wrongLetters = new HashSet<char>();
    private int score;
    private int roundScore;
    private int attempts;
    private int maxAttempts;
    private bool gameOver;
    private string vowels;
    private string recordFile;
    private int bestScore;
    private int round;
    private int totalRounds;

    public WheelOfFortune(string mode, string theme, string word)
    {
        this.mode = mode;
        this.theme = theme;
        this.word = string.IsNullOrEmpty(word) ? GetRandomWord() : word;
        this.displayWord = this.word.ToCharArray().Select(c => '_').ToArray();
        this.score = 0;
        this.roundScore = 0;
        this.attempts = 0;
        this.maxAttempts = 6;
        this.gameOver = false;
        this.vowels = "аеёиоуыэюя";
        this.recordFile = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), ".wheel_record.json");
        LoadRecord();
        this.round = 1;
        this.totalRounds = mode == "classic" ? 1 : mode == "tournament" ? 3 : 1;
    }

    string GetRandomWord()
    {
        var words = new Dictionary<string, List<string>>
        {
            {"животные", new List<string>{"кот","собака","тигр","слон","жираф","дельфин"}},
            {"растения", new List<string>{"роза","тюльпан","кактус","папоротник","дуб"}},
            {"страны", new List<string>{"россия","германия","франция","италия","япония"}},
            {"общее", new List<string>{"программирование","алгоритм","компьютер","интернет","игра"}}
        };
        var list = words.ContainsKey(theme) ? words[theme] : words["общее"];
        Random rnd = new Random();
        return list[rnd.Next(list.Count)];
    }

    void LoadRecord()
    {
        if (File.Exists(recordFile))
        {
            try
            {
                string json = File.ReadAllText(recordFile);
                var data = JsonSerializer.Deserialize<RecordData>(json);
                bestScore = data?.best_score ?? 0;
            }
            catch { bestScore = 0; }
        }
        else bestScore = 0;
    }

    void SaveRecord()
    {
        var data = new RecordData { best_score = bestScore };
        string json = JsonSerializer.Serialize(data);
        File.WriteAllText(recordFile, json);
    }

    void DisplayState()
    {
        Console.WriteLine(Colorize("==================================================", "bold"));
        Console.WriteLine(Colorize($"🎡 Колесо фортуны  |  Раунд {round}/{totalRounds}", "bold"));
        Console.WriteLine(Colorize($"Слово: {string.Join(" ", displayWord)}", "blue"));
        Console.WriteLine(Colorize($"Тема: {theme}", "cyan"));
        Console.WriteLine(Colorize($"Очки: {score}  |  Очки раунда: {roundScore}", "yellow"));
        Console.WriteLine(Colorize($"Открытые буквы: {(guessedLetters.Any() ? string.Join(", ", guessedLetters) : "нет")}", "green"));
        Console.WriteLine(Colorize($"Ошибочные буквы: {(wrongLetters.Any() ? string.Join(", ", wrongLetters) : "нет")}", "red"));
        Console.WriteLine(Colorize("==================================================", "bold"));
    }

    Sector SpinWheel()
    {
        Console.WriteLine(Colorize("🎡 Колесо вращается...", "bold"));
        Random rnd = new Random();
        for (int i=0; i<5; i++)
        {
            int idx = rnd.Next(SECTORS.Count);
            Console.Write($"\r{Colorize(SECTORS[idx].Name, SECTORS[idx].Color)}");
            Thread.Sleep(200);
        }
        int idx2 = rnd.Next(SECTORS.Count);
        Console.Write($"\r{Colorize(SECTORS[idx2].Name, SECTORS[idx2].Color)}\n");
        return SECTORS[idx2];
    }

    bool BuyVowel()
    {
        if (score < 100)
        {
            Console.WriteLine(Colorize("Недостаточно очков для покупки гласной (нужно 100).", "red"));
            return false;
        }
        var vowelsList = vowels.Where(v => !guessedLetters.Contains(v)).ToList();
        if (vowelsList.Count == 0)
        {
            Console.WriteLine(Colorize("Все гласные уже открыты.", "yellow"));
            return false;
        }
        char v = vowelsList[0];
        score -= 100;
        guessedLetters.Add(v);
        RevealLetter(v);
        Console.WriteLine(Colorize($"Вы купили гласную '{v}' за 100 очков.", "green"));
        return true;
    }

    int RevealLetter(char letter)
    {
        int count = 0;
        for (int i=0; i<word.Length; i++)
        {
            if (word[i] == letter)
            {
                displayWord[i] = letter;
                count++;
            }
        }
        return count;
    }

    bool GuessLetter(char letter)
    {
        if (guessedLetters.Contains(letter) || wrongLetters.Contains(letter))
        {
            Console.WriteLine(Colorize("Вы уже называли эту букву.", "yellow"));
            return false;
        }
        if (vowels.Contains(letter))
        {
            Console.WriteLine(Colorize("Гласные буквы можно только купить (команда buy).", "yellow"));
            return false;
        }
        if (word.Contains(letter))
        {
            guessedLetters.Add(letter);
            int count = RevealLetter(letter);
            roundScore += count * 50;
            Console.WriteLine(Colorize($"Буква '{letter}' есть в слове! ({count} раз)", "green"));
            if (!displayWord.Contains('_'))
            {
                WinRound();
            }
            return true;
        }
        else
        {
            wrongLetters.Add(letter);
            attempts++;
            Console.WriteLine(Colorize($"Буквы '{letter}' нет в слове.", "red"));
            if (mode == "survival")
            {
                roundScore = 0;
                Console.WriteLine(Colorize("Вы потеряли все очки раунда!", "red"));
            }
            if (attempts >= maxAttempts)
            {
                Console.WriteLine(Colorize("Попытки закончились. Ход переходит к компьютеру.", "yellow"));
                gameOver = true;
            }
            return false;
        }
    }

    bool GuessWord(string guess)
    {
        if (guess == word)
        {
            WinRound();
            return true;
        }
        else
        {
            attempts++;
            Console.WriteLine(Colorize("Неверно.", "red"));
            if (mode == "survival")
            {
                roundScore = 0;
                Console.WriteLine(Colorize("Вы потеряли все очки раунда!", "red"));
            }
            if (attempts >= maxAttempts) gameOver = true;
            return false;
        }
    }

    void WinRound()
    {
        Console.WriteLine(Colorize($"🎉 Вы отгадали слово '{word}'!", "green"));
        Console.WriteLine(Colorize($"Вы заработали {roundScore} очков в этом раунде.", "yellow"));
        score += roundScore;
        if (mode == "classic" || round >= totalRounds)
        {
            gameOver = true;
        }
        else
        {
            round++;
            attempts = 0;
            roundScore = 0;
            word = GetRandomWord();
            displayWord = word.ToCharArray().Select(c => '_').ToArray();
            guessedLetters.Clear();
            wrongLetters.Clear();
            Console.WriteLine(Colorize($"Переход к раунду {round}", "cyan"));
        }
    }

    public void PlayTurn()
    {
        while (!gameOver)
        {
            DisplayState();
            Console.WriteLine("\nДействия: spin, letter <буква>, guess <слово>, buy, quit");
            Console.Write("> ");
            string cmd = Console.ReadLine().Trim();
            if (cmd == "quit") { Console.WriteLine("Выход."); return; }
            if (cmd == "spin")
            {
                Sector sector = SpinWheel();
                if (sector.Value > 0)
                {
                    Console.WriteLine(Colorize($"Выпало {sector.Value} очков!", "green"));
                    Console.Write("Введите букву: ");
                    string input = Console.ReadLine().Trim();
                    if (input.Length != 1 || !char.IsLetter(input[0]))
                    {
                        Console.WriteLine(Colorize("Введите одну букву.", "red"));
                        continue;
                    }
                    char letter = char.ToLower(input[0]);
                    if (vowels.Contains(letter))
                    {
                        Console.WriteLine(Colorize("Гласные нужно покупать (команда buy).", "yellow"));
                        continue;
                    }
                    if (GuessLetter(letter))
                    {
                        int count = word.Count(c => c == letter);
                        roundScore += count * sector.Value;
                    }
                    else
                    {
                        Console.WriteLine(Colorize("Ошибка! Ход переходит к компьютеру.", "red"));
                        gameOver = true;
                    }
                }
                else if (sector.IsBonus)
                {
                    Console.WriteLine(Colorize("Бонус x2! Ваши очки удваиваются!", "magenta"));
                    roundScore *= 2;
                }
                else if (sector.IsBankrupt)
                {
                    Console.WriteLine(Colorize("Банкрот! Вы теряете все очки раунда!", "red"));
                    roundScore = 0;
                }
                else if (sector.IsGift)
                {
                    Console.WriteLine(Colorize("Подарок! Вы получаете дополнительный ход!", "cyan"));
                    continue;
                }
                else if (sector.IsMystery)
                {
                    Random rnd = new Random();
                    int bonus = rnd.Next(50, 301);
                    Console.WriteLine(Colorize($"Загадка! Вы получаете {bonus} бонусных очков!", "yellow"));
                    roundScore += bonus;
                }
            }
            else if (cmd.StartsWith("letter "))
            {
                string part = cmd.Substring(7);
                if (part.Length != 1 || !char.IsLetter(part[0]))
                {
                    Console.WriteLine(Colorize("Введите одну букву.", "red"));
                    continue;
                }
                GuessLetter(char.ToLower(part[0]));
            }
            else if (cmd.StartsWith("guess "))
            {
                string guess = cmd.Substring(6);
                GuessWord(guess);
            }
            else if (cmd == "buy")
            {
                if (mode == "classic") BuyVowel();
                else Console.WriteLine(Colorize("В этом режиме покупка гласных недоступна.", "red"));
            }
            else
            {
                Console.WriteLine(Colorize("Неизвестная команда.", "red"));
            }
        }
        if (gameOver && !displayWord.Contains('_'))
        {
            WinRound();
        }
        else if (gameOver && displayWord.Contains('_'))
        {
            Console.WriteLine(Colorize($"Игра окончена. Загаданное слово: {word}", "red"));
            Console.WriteLine(Colorize($"Вы заработали {score} очков.", "yellow"));
        }
        if (score > bestScore)
        {
            bestScore = score;
            SaveRecord();
            Console.WriteLine(Colorize($"🏆 Новый рекорд: {bestScore} очков!", "green"));
        }
    }

    static void Main(string[] args)
    {
        string mode = "classic", theme = "общее", word = "";
        bool showStats = false, reset = false;
        for (int i=0; i<args.Length; i++)
        {
            string arg = args[i];
            if (arg == "classic" || arg == "survival" || arg == "tournament") mode = arg;
            else if (arg == "-w" && i+1 < args.Length) word = args[++i];
            else if (arg == "-t" && i+1 < args.Length) theme = args[++i];
            else if (arg == "-s" || arg == "--stats") showStats = true;
            else if (arg == "-r" || arg == "--reset") reset = true;
            else if (arg == "-h" || arg == "--help")
            {
                Console.WriteLine("Usage: wheel [classic|survival|tournament] [-w word] [-t theme] [-s] [-r]");
                return;
            }
        }
        if (reset)
        {
            string f = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), ".wheel_record.json");
            if (File.Exists(f)) File.Delete(f);
            Console.WriteLine("Рекорды сброшены.");
            return;
        }
        if (showStats)
        {
            string f = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), ".wheel_record.json");
            if (File.Exists(f))
            {
                try
                {
                    string json = File.ReadAllText(f);
                    var data = JsonSerializer.Deserialize<RecordData>(json);
                    Console.WriteLine($"Лучший результат: {data?.best_score ?? 0} очков");
                }
                catch { Console.WriteLine("Рекордов пока нет."); }
            }
            else Console.WriteLine("Рекордов пока нет.");
            return;
        }
        WheelOfFortune game = new WheelOfFortune(mode, theme, word);
        game.PlayTurn();
    }
}
