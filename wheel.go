// wheel.go
package main

import (
	"bufio"
	"encoding/json"
	"fmt"
	"math/rand"
	"os"
	"path/filepath"
	"strings"
	"time"
)

const (
	reset  = "\033[0m"
	red    = "\033[91m"
	green  = "\033[92m"
	yellow = "\033[93m"
	blue   = "\033[94m"
	magenta= "\033[95m"
	cyan   = "\033[96m"
	bold   = "\033[1m"
)

func colorize(text, color string) string {
	return color + text + reset
}

type Sector struct {
	Name     string
	Color    string
	Value    int
	IsBonus  bool
	IsBankrupt bool
	IsGift   bool
	IsMystery bool
}

var sectors = []Sector{
	{"100", green, 100, false, false, false, false},
	{"200", green, 200, false, false, false, false},
	{"300", green, 300, false, false, false, false},
	{"500", green, 500, false, false, false, false},
	{"800", green, 800, false, false, false, false},
	{"1000", green, 1000, false, false, false, false},
	{"Бонус x2", magenta, 0, true, false, false, false},
	{"Банкрот", red, 0, false, true, false, false},
	{"Подарок", cyan, 0, false, false, true, false},
	{"Загадка", yellow, 0, false, false, false, true},
}

type Record struct {
	BestScore int `json:"best_score"`
}

type Game struct {
	mode         string
	theme        string
	word         string
	displayWord  []rune
	guessed      map[rune]bool
	wrong        map[rune]bool
	score        int
	roundScore   int
	attempts     int
	maxAttempts  int
	gameOver     bool
	vowels       string
	recordFile   string
	bestScore    int
	round        int
	totalRounds  int
}

func NewGame(mode, theme, word string) *Game {
	g := &Game{
		mode:        mode,
		theme:       theme,
		attempts:    0,
		maxAttempts: 6,
		gameOver:    false,
		vowels:      "аеёиоуыэюя",
		round:       1,
		guessed:     make(map[rune]bool),
		wrong:       make(map[rune]bool),
	}
	if word == "" {
		g.word = g.getRandomWord()
	} else {
		g.word = word
	}
	g.displayWord = make([]rune, len(g.word))
	for i := range g.displayWord {
		g.displayWord[i] = '_'
	}
	g.totalRounds = 1
	if g.mode == "tournament" {
		g.totalRounds = 3
	}
	g.recordFile = filepath.Join(os.Getenv("HOME"), ".wheel_record.json")
	g.loadRecord()
	return g
}

func (g *Game) getRandomWord() string {
	words := map[string][]string{
		"животные": {"кот", "собака", "тигр", "слон", "жираф", "дельфин"},
		"растения": {"роза", "тюльпан", "кактус", "папоротник", "дуб"},
		"страны":   {"россия", "германия", "франция", "италия", "япония"},
		"общее":    {"программирование", "алгоритм", "компьютер", "интернет", "игра"},
	}
	list, ok := words[g.theme]
	if !ok || len(list) == 0 {
		list = words["общее"]
	}
	return list[rand.Intn(len(list))]
}

func (g *Game) loadRecord() {
	data, err := os.ReadFile(g.recordFile)
	if err != nil {
		g.bestScore = 0
		return
	}
	var rec Record
	if err := json.Unmarshal(data, &rec); err != nil {
		g.bestScore = 0
	} else {
		g.bestScore = rec.BestScore
	}
}

func (g *Game) saveRecord() {
	data, _ := json.Marshal(Record{BestScore: g.bestScore})
	os.WriteFile(g.recordFile, data, 0644)
}

func (g *Game) displayState() {
	fmt.Println(colorize("==================================================", bold))
	fmt.Printf("%s\n", colorize(fmt.Sprintf("🎡 Колесо фортуны  |  Раунд %d/%d", g.round, g.totalRounds), bold))
	fmt.Printf("%s: ", colorize("Слово", blue))
	for _, ch := range g.displayWord {
		fmt.Printf("%c ", ch)
	}
	fmt.Println()
	fmt.Printf("%s: %s\n", colorize("Тема", cyan), g.theme)
	fmt.Printf("%s: %d  |  %s: %d\n", colorize("Очки", yellow), g.score, colorize("Очки раунда", yellow), g.roundScore)
	fmt.Printf("%s: ", colorize("Открытые буквы", green))
	if len(g.guessed) == 0 {
		fmt.Print("нет")
	} else {
		for ch := range g.guessed {
			fmt.Printf("%c ", ch)
		}
	}
	fmt.Println()
	fmt.Printf("%s: ", colorize("Ошибочные буквы", red))
	if len(g.wrong) == 0 {
		fmt.Print("нет")
	} else {
		for ch := range g.wrong {
			fmt.Printf("%c ", ch)
		}
	}
	fmt.Println()
	fmt.Println(colorize("==================================================", bold))
}

func (g *Game) spinWheel() Sector {
	fmt.Println(colorize("🎡 Колесо вращается...", bold))
	for i := 0; i < 5; i++ {
		idx := rand.Intn(len(sectors))
		fmt.Printf("\r%s", colorize(sectors[idx].Name, sectors[idx].Color))
		time.Sleep(200 * time.Millisecond)
	}
	idx := rand.Intn(len(sectors))
	fmt.Printf("\r%s\n", colorize(sectors[idx].Name, sectors[idx].Color))
	return sectors[idx]
}

func (g *Game) buyVowel() bool {
	if g.score < 100 {
		fmt.Println(colorize("Недостаточно очков для покупки гласной (нужно 100).", red))
		return false
	}
	var vowelsList []rune
	for _, v := range g.vowels {
		if !g.guessed[v] {
			vowelsList = append(vowelsList, v)
		}
	}
	if len(vowelsList) == 0 {
		fmt.Println(colorize("Все гласные уже открыты.", yellow))
		return false
	}
	v := vowelsList[0]
	g.score -= 100
	g.guessed[v] = true
	g.revealLetter(v)
	fmt.Printf("%s\n", colorize(fmt.Sprintf("Вы купили гласную '%c' за 100 очков.", v), green))
	return true
}

func (g *Game) revealLetter(ch rune) int {
	count := 0
	for i, c := range g.word {
		if c == ch {
			g.displayWord[i] = ch
			count++
		}
	}
	return count
}

func (g *Game) guessLetter(ch rune) bool {
	if g.guessed[ch] || g.wrong[ch] {
		fmt.Println(colorize("Вы уже называли эту букву.", yellow))
		return false
	}
	if strings.ContainsRune(g.vowels, ch) {
		fmt.Println(colorize("Гласные буквы можно только купить (команда buy).", yellow))
		return false
	}
	if strings.ContainsRune(g.word, ch) {
		g.guessed[ch] = true
		count := g.revealLetter(ch)
		g.roundScore += count * 50
		fmt.Printf("%s\n", colorize(fmt.Sprintf("Буква '%c' есть в слове! (%d раз)", ch, count), green))
		if !strings.ContainsRune(string(g.displayWord), '_') {
			g.winRound()
		}
		return true
	} else {
		g.wrong[ch] = true
		g.attempts++
		fmt.Printf("%s\n", colorize(fmt.Sprintf("Буквы '%c' нет в слове.", ch), red))
		if g.mode == "survival" {
			g.roundScore = 0
			fmt.Println(colorize("Вы потеряли все очки раунда!", red))
		}
		if g.attempts >= g.maxAttempts {
			fmt.Println(colorize("Попытки закончились. Ход переходит к компьютеру.", yellow))
			g.gameOver = true
		}
		return false
	}
}

func (g *Game) guessWord(guess string) bool {
	if guess == g.word {
		g.winRound()
		return true
	} else {
		g.attempts++
		fmt.Println(colorize("Неверно.", red))
		if g.mode == "survival" {
			g.roundScore = 0
			fmt.Println(colorize("Вы потеряли все очки раунда!", red))
		}
		if g.attempts >= g.maxAttempts {
			g.gameOver = true
		}
		return false
	}
}

func (g *Game) winRound() {
	fmt.Printf("%s\n", colorize(fmt.Sprintf("🎉 Вы отгадали слово '%s'!", g.word), green))
	fmt.Printf("%s\n", colorize(fmt.Sprintf("Вы заработали %d очков в этом раунде.", g.roundScore), yellow))
	g.score += g.roundScore
	if g.mode == "classic" || g.round >= g.totalRounds {
		g.gameOver = true
	} else {
		g.round++
		g.attempts = 0
		g.roundScore = 0
		g.word = g.getRandomWord()
		g.displayWord = make([]rune, len(g.word))
		for i := range g.displayWord {
			g.displayWord[i] = '_'
		}
		g.guessed = make(map[rune]bool)
		g.wrong = make(map[rune]bool)
		fmt.Printf("%s\n", colorize(fmt.Sprintf("Переход к раунду %d", g.round), cyan))
	}
}

func (g *Game) playTurn() {
	scanner := bufio.NewScanner(os.Stdin)
	for !g.gameOver {
		g.displayState()
		fmt.Println("\nДействия: spin, letter <буква>, guess <слово>, buy, quit")
		fmt.Print("> ")
		if !scanner.Scan() {
			break
		}
		cmd := strings.TrimSpace(scanner.Text())
		if cmd == "quit" {
			fmt.Println("Выход.")
			return
		}
		if cmd == "spin" {
			sector := g.spinWheel()
			if sector.Value > 0 {
				fmt.Printf("%s\n", colorize(fmt.Sprintf("Выпало %d очков!", sector.Value), green))
				fmt.Print("Введите букву: ")
				if !scanner.Scan() {
					break
				}
				letterStr := strings.TrimSpace(scanner.Text())
				if len(letterStr) != 1 {
					fmt.Println(colorize("Введите одну букву.", red))
					continue
				}
				ch := []rune(letterStr)[0]
				if strings.ContainsRune(g.vowels, ch) {
					fmt.Println(colorize("Гласные нужно покупать (команда buy).", yellow))
					continue
				}
				if g.guessLetter(ch) {
					count := strings.Count(g.word, string(ch))
					g.roundScore += count * sector.Value
				} else {
					fmt.Println(colorize("Ошибка! Ход переходит к компьютеру.", red))
					g.gameOver = true
				}
			} else if sector.IsBonus {
				fmt.Println(colorize("Бонус x2! Ваши очки удваиваются!", magenta))
				g.roundScore *= 2
			} else if sector.IsBankrupt {
				fmt.Println(colorize("Банкрот! Вы теряете все очки раунда!", red))
				g.roundScore = 0
			} else if sector.IsGift {
				fmt.Println(colorize("Подарок! Вы получаете дополнительный ход!", cyan))
				continue
			} else if sector.IsMystery {
				bonus := rand.Intn(251) + 50
				fmt.Printf("%s\n", colorize(fmt.Sprintf("Загадка! Вы получаете %d бонусных очков!", bonus), yellow))
				g.roundScore += bonus
			}
		} else if strings.HasPrefix(cmd, "letter ") {
			parts := strings.SplitN(cmd, " ", 2)
			if len(parts) != 2 || len(parts[1]) != 1 {
				fmt.Println(colorize("Введите одну букву.", red))
				continue
			}
			ch := []rune(parts[1])[0]
			g.guessLetter(ch)
		} else if strings.HasPrefix(cmd, "guess ") {
			parts := strings.SplitN(cmd, " ", 2)
			if len(parts) != 2 {
				fmt.Println(colorize("Введите слово.", red))
				continue
			}
			g.guessWord(parts[1])
		} else if cmd == "buy" {
			if g.mode == "classic" {
				g.buyVowel()
			} else {
				fmt.Println(colorize("В этом режиме покупка гласных недоступна.", red))
			}
		} else {
			fmt.Println(colorize("Неизвестная команда.", red))
		}
	}
	// Конец игры
	if g.gameOver && !strings.ContainsRune(string(g.displayWord), '_') {
		g.winRound()
	} else if g.gameOver && strings.ContainsRune(string(g.displayWord), '_') {
		fmt.Printf("%s\n", colorize(fmt.Sprintf("Игра окончена. Загаданное слово: %s", g.word), red))
		fmt.Printf("%s\n", colorize(fmt.Sprintf("Вы заработали %d очков.", g.score), yellow))
	}
	if g.score > g.bestScore {
		g.bestScore = g.score
		g.saveRecord()
		fmt.Printf("%s\n", colorize(fmt.Sprintf("🏆 Новый рекорд: %d очков!", g.bestScore), green))
	}
}

func main() {
	rand.Seed(time.Now().UnixNano())
	mode := "classic"
	theme := "общее"
	word := ""
	showStats := false
	reset := false
	args := os.Args[1:]
	for i := 0; i < len(args); i++ {
		arg := args[i]
		switch arg {
		case "classic", "survival", "tournament":
			mode = arg
		case "-w":
			if i+1 < len(args) {
				word = args[i+1]
				i++
			}
		case "-t":
			if i+1 < len(args) {
				theme = args[i+1]
				i++
			}
		case "-s", "--stats":
			showStats = true
		case "-r", "--reset":
			reset = true
		case "-h", "--help":
			fmt.Println("Usage: wheel [classic|survival|tournament] [-w word] [-t theme] [-s] [-r]")
			return
		}
	}
	if reset {
		f := filepath.Join(os.Getenv("HOME"), ".wheel_record.json")
		os.Remove(f)
		fmt.Println("Рекорды сброшены.")
		return
	}
	if showStats {
		f := filepath.Join(os.Getenv("HOME"), ".wheel_record.json")
		data, err := os.ReadFile(f)
		if err != nil {
			fmt.Println("Рекордов пока нет.")
			return
		}
		var rec Record
		if err := json.Unmarshal(data, &rec); err != nil {
			fmt.Println("Рекордов пока нет.")
			return
		}
		fmt.Printf("Лучший результат: %d очков\n", rec.BestScore)
		return
	}
	game := NewGame(mode, theme, word)
	game.playTurn()
}
