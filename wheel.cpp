// wheel.cpp
#include <iostream>
#include <vector>
#include <string>
#include <map>
#include <random>
#include <fstream>
#include <algorithm>
#include <cctype>
#include <chrono>
#include <thread>
#include <filesystem>

using namespace std;
namespace fs = std::filesystem;

const string RESET = "\033[0m";
const string RED = "\033[91m";
const string GREEN = "\033[92m";
const string YELLOW = "\033[93m";
const string BLUE = "\033[94m";
const string MAGENTA = "\033[95m";
const string CYAN = "\033[96m";
const string BOLD = "\033[1m";

string colorize(const string& text, const string& color) {
    return color + text + RESET;
}

struct Sector {
    string name;
    string color;
    int value;
    bool isBonus;
    bool isBankrupt;
    bool isGift;
    bool isMystery;
};

vector<Sector> SECTORS = {
    {"100", GREEN, 100, false, false, false, false},
    {"200", GREEN, 200, false, false, false, false},
    {"300", GREEN, 300, false, false, false, false},
    {"500", GREEN, 500, false, false, false, false},
    {"800", GREEN, 800, false, false, false, false},
    {"1000", GREEN, 1000, false, false, false, false},
    {"Бонус x2", MAGENTA, 0, true, false, false, false},
    {"Банкрот", RED, 0, false, true, false, false},
    {"Подарок", CYAN, 0, false, false, true, false},
    {"Загадка", YELLOW, 0, false, false, false, true}
};

string getHomeDir() {
    const char* home = getenv("HOME");
    if (!home) home = getenv("USERPROFILE");
    return string(home);
}

class WheelOfFortune {
public:
    string mode;
    string theme;
    string word;
    vector<char> displayWord;
    set<char> guessedLetters;
    set<char> wrongLetters;
    int score;
    int roundScore;
    int attempts;
    int maxAttempts;
    bool gameOver;
    string vowels;
    string recordFile;
    int bestScore;
    int round;
    int totalRounds;

    WheelOfFortune(string m, string t, string w) : mode(m), theme(t), word(w), score(0), roundScore(0),
        attempts(0), maxAttempts(6), gameOver(false), vowels("аеёиоуыэюя"), round(1) {
        if (word.empty()) word = getRandomWord();
        displayWord = vector<char>(word.size(), '_');
        totalRounds = (mode == "classic") ? 1 : (mode == "tournament") ? 3 : 1;
        recordFile = getHomeDir() + "/.wheel_record.json";
        loadRecord();
    }

    string getRandomWord() {
        map<string, vector<string>> words = {
            {"животные", {"кот","собака","тигр","слон","жираф","дельфин"}},
            {"растения", {"роза","тюльпан","кактус","папоротник","дуб"}},
            {"страны", {"россия","германия","франция","италия","япония"}},
            {"общее", {"программирование","алгоритм","компьютер","интернет","игра"}}
        };
        auto& vec = words[theme];
        if (vec.empty()) vec = words["общее"];
        random_device rd;
        mt19937 gen(rd());
        uniform_int_distribution<> dis(0, vec.size()-1);
        return vec[dis(gen)];
    }

    void loadRecord() {
        ifstream f(recordFile);
        if (!f) { bestScore = 0; return; }
        string content((istreambuf_iterator<char>(f)), istreambuf_iterator<char>());
        size_t pos = content.find("\"best_score\"");
        if (pos != string::npos) {
            size_t start = content.find(":", pos) + 1;
            size_t end = content.find(",", start);
            if (end == string::npos) end = content.find("}", start);
            try { bestScore = stoi(content.substr(start, end-start)); }
            catch (...) { bestScore = 0; }
        } else bestScore = 0;
    }

    void saveRecord() {
        ofstream f(recordFile);
        if (f) f << "{\"best_score\":" << bestScore << "}";
    }

    void displayState() {
        cout << colorize("=" + string(50, '=') + "=", BOLD) << endl;
        cout << colorize("🎡 Колесо фортуны  |  Раунд " + to_string(round) + "/" + to_string(totalRounds), BOLD) << endl;
        cout << colorize("Слово: ", BLUE);
        for (char c : displayWord) cout << c << " ";
        cout << endl;
        cout << colorize("Тема: " + theme, CYAN) << endl;
        cout << colorize("Очки: " + to_string(score) + "  |  Очки раунда: " + to_string(roundScore), YELLOW) << endl;
        cout << colorize("Открытые буквы: ", GREEN);
        for (char c : guessedLetters) cout << c << " ";
        if (guessedLetters.empty()) cout << "нет";
        cout << endl;
        cout << colorize("Ошибочные буквы: ", RED);
        for (char c : wrongLetters) cout << c << " ";
        if (wrongLetters.empty()) cout << "нет";
        cout << endl;
        cout << colorize("=" + string(50, '=') + "=", BOLD) << endl;
    }

    Sector spinWheel() {
        cout << colorize("🎡 Колесо вращается...", BOLD) << endl;
        for (int i=0; i<5; ++i) {
            int idx = rand() % SECTORS.size();
            cout << "\r" << colorize(SECTORS[idx].name, SECTORS[idx].color) << flush;
            this_thread::sleep_for(chrono::milliseconds(200));
        }
        int idx = rand() % SECTORS.size();
        cout << "\r" << colorize(SECTORS[idx].name, SECTORS[idx].color) << endl;
        return SECTORS[idx];
    }

    bool buyVowel() {
        if (score < 100) {
            cout << colorize("Недостаточно очков для покупки гласной (нужно 100).", RED) << endl;
            return false;
        }
        vector<char> vowelsList;
        for (char v : vowels)
            if (guessedLetters.find(v) == guessedLetters.end())
                vowelsList.push_back(v);
        if (vowelsList.empty()) {
            cout << colorize("Все гласные уже открыты.", YELLOW) << endl;
            return false;
        }
        char v = vowelsList[0];
        score -= 100;
        guessedLetters.insert(v);
        revealLetter(v);
        cout << colorize("Вы купили гласную '" + string(1, v) + "' за 100 очков.", GREEN) << endl;
        return true;
    }

    int revealLetter(char letter) {
        int count = 0;
        for (size_t i=0; i<word.size(); ++i) {
            if (word[i] == letter) {
                displayWord[i] = letter;
                count++;
            }
        }
        return count;
    }

    bool guessLetter(char letter) {
        if (guessedLetters.find(letter) != guessedLetters.end() || wrongLetters.find(letter) != wrongLetters.end()) {
            cout << colorize("Вы уже называли эту букву.", YELLOW) << endl;
            return false;
        }
        if (vowels.find(letter) != string::npos) {
            cout << colorize("Гласные буквы можно только купить (команда buy).", YELLOW) << endl;
            return false;
        }
        if (word.find(letter) != string::npos) {
            guessedLetters.insert(letter);
            int count = revealLetter(letter);
            roundScore += count * 50;
            cout << colorize("Буква '" + string(1, letter) + "' есть в слове! (" + to_string(count) + " раз)", GREEN) << endl;
            if (find(displayWord.begin(), displayWord.end(), '_') == displayWord.end()) {
                winRound();
            }
            return true;
        } else {
            wrongLetters.insert(letter);
            attempts++;
            cout << colorize("Буквы '" + string(1, letter) + "' нет в слове.", RED) << endl;
            if (mode == "survival") {
                roundScore = 0;
                cout << colorize("Вы потеряли все очки раунда!", RED) << endl;
            }
            if (attempts >= maxAttempts) {
                cout << colorize("Попытки закончились. Ход переходит к компьютеру.", YELLOW) << endl;
                gameOver = true;
            }
            return false;
        }
    }

    bool guessWord(const string& g) {
        if (g == word) {
            winRound();
            return true;
        } else {
            attempts++;
            cout << colorize("Неверно.", RED) << endl;
            if (mode == "survival") {
                roundScore = 0;
                cout << colorize("Вы потеряли все очки раунда!", RED) << endl;
            }
            if (attempts >= maxAttempts) {
                gameOver = true;
            }
            return false;
        }
    }

    void winRound() {
        cout << colorize("🎉 Вы отгадали слово '" + word + "'!", GREEN) << endl;
        cout << colorize("Вы заработали " + to_string(roundScore) + " очков в этом раунде.", YELLOW) << endl;
        score += roundScore;
        if (mode == "classic" || round >= totalRounds) {
            gameOver = true;
        } else {
            round++;
            attempts = 0;
            roundScore = 0;
            word = getRandomWord();
            displayWord = vector<char>(word.size(), '_');
            guessedLetters.clear();
            wrongLetters.clear();
            cout << colorize("Переход к раунду " + to_string(round), CYAN) << endl;
        }
    }

    void playTurn() {
        while (!gameOver) {
            displayState();
            cout << "\nДействия: spin, letter <буква>, guess <слово>, buy, quit" << endl;
            string cmd;
            getline(cin, cmd);
            if (cmd == "quit") { cout << "Выход." << endl; return; }
            if (cmd == "spin") {
                Sector sector = spinWheel();
                if (sector.value > 0) {
                    cout << colorize("Выпало " + to_string(sector.value) + " очков!", GREEN) << endl;
                    cout << "Введите букву: ";
                    string input;
                    getline(cin, input);
                    if (input.size() != 1 || !isalpha(input[0])) {
                        cout << colorize("Введите одну букву.", RED) << endl;
                        continue;
                    }
                    char letter = tolower(input[0]);
                    if (vowels.find(letter) != string::npos) {
                        cout << colorize("Гласные нужно покупать (команда buy).", YELLOW) << endl;
                        continue;
                    }
                    if (guessLetter(letter)) {
                        int count = count_if(word.begin(), word.end(), [letter](char c){return c==letter;});
                        roundScore += count * sector.value;
                    } else {
                        cout << colorize("Ошибка! Ход переходит к компьютеру.", RED) << endl;
                        gameOver = true;
                    }
                } else if (sector.isBonus) {
                    cout << colorize("Бонус x2! Ваши очки удваиваются!", MAGENTA) << endl;
                    roundScore *= 2;
                } else if (sector.isBankrupt) {
                    cout << colorize("Банкрот! Вы теряете все очки раунда!", RED) << endl;
                    roundScore = 0;
                } else if (sector.isGift) {
                    cout << colorize("Подарок! Вы получаете дополнительный ход!", CYAN) << endl;
                    continue;
                } else if (sector.isMystery) {
                    int bonus = rand() % 251 + 50;
                    cout << colorize("Загадка! Вы получаете " + to_string(bonus) + " бонусных очков!", YELLOW) << endl;
                    roundScore += bonus;
                }
            } else if (cmd.substr(0,7) == "letter ") {
                char letter = cmd[7];
                if (!isalpha(letter)) { cout << colorize("Введите одну букву.", RED) << endl; continue; }
                guessLetter(tolower(letter));
            } else if (cmd.substr(0,6) == "guess ") {
                string guess = cmd.substr(6);
                guessWord(guess);
            } else if (cmd == "buy") {
                if (mode == "classic") buyVowel();
                else cout << colorize("В этом режиме покупка гласных недоступна.", RED) << endl;
            } else {
                cout << colorize("Неизвестная команда.", RED) << endl;
            }
        }
        // Конец игры
        if (gameOver && find(displayWord.begin(), displayWord.end(), '_') == displayWord.end()) {
            winRound();
        } else if (gameOver && find(displayWord.begin(), displayWord.end(), '_') != displayWord.end()) {
            cout << colorize("Игра окончена. Загаданное слово: " + word, RED) << endl;
            cout << colorize("Вы заработали " + to_string(score) + " очков.", YELLOW) << endl;
        }
        if (score > bestScore) {
            bestScore = score;
            saveRecord();
            cout << colorize("🏆 Новый рекорд: " + to_string(bestScore) + " очков!", GREEN) << endl;
        }
    }
};

int main(int argc, char* argv[]) {
    string mode = "classic", theme = "общее", word = "";
    bool showStats = false, reset = false;
    for (int i=1; i<argc; ++i) {
        string arg = argv[i];
        if (arg == "classic" || arg == "survival" || arg == "tournament") mode = arg;
        else if (arg == "-w" && i+1 < argc) word = argv[++i];
        else if (arg == "-t" && i+1 < argc) theme = argv[++i];
        else if (arg == "-s" || arg == "--stats") showStats = true;
        else if (arg == "-r" || arg == "--reset") reset = true;
        else if (arg == "-h" || arg == "--help") {
            cout << "Usage: wheel [classic|survival|tournament] [-w word] [-t theme] [-s] [-r]" << endl;
            return 0;
        }
    }
    if (reset) {
        string f = getHomeDir() + "/.wheel_record.json";
        if (fs::exists(f)) fs::remove(f);
        cout << "Рекорды сброшены." << endl;
        return 0;
    }
    if (showStats) {
        string f = getHomeDir() + "/.wheel_record.json";
        ifstream file(f);
        if (file) {
            string content((istreambuf_iterator<char>(file)), istreambuf_iterator<char>());
            size_t pos = content.find("\"best_score\"");
            if (pos != string::npos) {
                size_t start = content.find(":", pos) + 1;
                size_t end = content.find(",", start);
                if (end == string::npos) end = content.find("}", start);
                try { cout << "Лучший результат: " << stoi(content.substr(start, end-start)) << " очков" << endl; }
                catch (...) { cout << "Рекордов пока нет." << endl; }
            } else cout << "Рекордов пока нет." << endl;
        } else cout << "Рекордов пока нет." << endl;
        return 0;
    }
    srand(time(nullptr));
    WheelOfFortune game(mode, theme, word);
    game.playTurn();
    return 0;
}
