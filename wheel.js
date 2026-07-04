// wheel.js
#!/usr/bin/env node
'use strict';

const fs = require('fs');
const path = require('path');
const os = require('os');
const readline = require('readline');

const COLORS = {
    reset: '\x1b[0m',
    red: '\x1b[91m',
    green: '\x1b[92m',
    yellow: '\x1b[93m',
    blue: '\x1b[94m',
    magenta: '\x1b[95m',
    cyan: '\x1b[96m',
    bold: '\x1b[1m'
};

function colorize(text, color) {
    return COLORS[color] + text + COLORS.reset;
}

const SECTORS = [
    { name: '100', color: 'green', value: 100 },
    { name: '200', color: 'green', value: 200 },
    { name: '300', color: 'green', value: 300 },
    { name: '500', color: 'green', value: 500 },
    { name: '800', color: 'green', value: 800 },
    { name: '1000', color: 'green', value: 1000 },
    { name: 'Бонус x2', color: 'magenta', value: 'bonus' },
    { name: 'Банкрот', color: 'red', value: 'bankrupt' },
    { name: 'Подарок', color: 'cyan', value: 'gift' },
    { name: 'Загадка', color: 'yellow', value: 'mystery' }
];

class WheelOfFortune {
    constructor(mode, theme, word) {
        this.mode = mode;
        this.theme = theme;
        this.word = word || this.getRandomWord();
        this.displayWord = Array(this.word.length).fill('_');
        this.guessed = new Set();
        this.wrong = new Set();
        this.score = 0;
        this.roundScore = 0;
        this.attempts = 0;
        this.maxAttempts = 6;
        this.gameOver = false;
        this.vowels = 'аеёиоуыэюя';
        this.recordFile = path.join(os.homedir(), '.wheel_record.json');
        this.loadRecord();
        this.round = 1;
        this.totalRounds = (mode === 'classic') ? 1 : (mode === 'tournament') ? 3 : 1;
    }

    getRandomWord() {
        const words = {
            'животные': ['кот','собака','тигр','слон','жираф','дельфин'],
            'растения': ['роза','тюльпан','кактус','папоротник','дуб'],
            'страны': ['россия','германия','франция','италия','япония'],
            'общее': ['программирование','алгоритм','компьютер','интернет','игра']
        };
        const list = words[this.theme] || words['общее'];
        return list[Math.floor(Math.random() * list.length)];
    }

    loadRecord() {
        try {
            const data = JSON.parse(fs.readFileSync(this.recordFile, 'utf8'));
            this.bestScore = data.best_score || 0;
        } catch {
            this.bestScore = 0;
        }
    }

    saveRecord() {
        fs.writeFileSync(this.recordFile, JSON.stringify({ best_score: this.bestScore }));
    }

    displayState() {
        console.log(colorize('='.repeat(50), 'bold'));
        console.log(colorize(`🎡 Колесо фортуны  |  Раунд ${this.round}/${this.totalRounds}`, 'bold'));
        console.log(colorize(`Слово: ${this.displayWord.join(' ')}`, 'blue'));
        console.log(colorize(`Тема: ${this.theme}`, 'cyan'));
        console.log(colorize(`Очки: ${this.score}  |  Очки раунда: ${this.roundScore}`, 'yellow'));
        console.log(colorize(`Открытые буквы: ${[...this.guessed].join(', ') || 'нет'}`, 'green'));
        console.log(colorize(`Ошибочные буквы: ${[...this.wrong].join(', ') || 'нет'}`, 'red'));
        console.log(colorize('='.repeat(50), 'bold'));
    }

    spinWheel() {
        console.log(colorize('🎡 Колесо вращается...', 'bold'));
        for (let i=0; i<5; i++) {
            const idx = Math.floor(Math.random() * SECTORS.length);
            process.stdout.write(`\r${colorize(SECTORS[idx].name, SECTORS[idx].color)}`);
            setTimeout(() => {}, 100);
        }
        const idx = Math.floor(Math.random() * SECTORS.length);
        const sector = SECTORS[idx];
        process.stdout.write(`\r${colorize(sector.name, sector.color)}\n`);
        return sector;
    }

    buyVowel() {
        if (this.score < 100) {
            console.log(colorize('Недостаточно очков для покупки гласной (нужно 100).', 'red'));
            return false;
        }
        const vowels = [...this.vowels].filter(v => !this.guessed.has(v));
        if (vowels.length === 0) {
            console.log(colorize('Все гласные уже открыты.', 'yellow'));
            return false;
        }
        const v = vowels[0];
        this.score -= 100;
        this.guessed.add(v);
        this.revealLetter(v);
        console.log(colorize(`Вы купили гласную '${v}' за 100 очков.`, 'green'));
        return true;
    }

    revealLetter(ch) {
        let count = 0;
        for (let i=0; i<this.word.length; i++) {
            if (this.word[i] === ch) {
                this.displayWord[i] = ch;
                count++;
            }
        }
        return count;
    }

    guessLetter(ch) {
        if (this.guessed.has(ch) || this.wrong.has(ch)) {
            console.log(colorize('Вы уже называли эту букву.', 'yellow'));
            return false;
        }
        if (this.vowels.includes(ch)) {
            console.log(colorize('Гласные буквы можно только купить (команда buy).', 'yellow'));
            return false;
        }
        if (this.word.includes(ch)) {
            this.guessed.add(ch);
            const count = this.revealLetter(ch);
            this.roundScore += count * 50;
            console.log(colorize(`Буква '${ch}' есть в слове! (${count} раз)`, 'green'));
            if (!this.displayWord.includes('_')) {
                this.winRound();
            }
            return true;
        } else {
            this.wrong.add(ch);
            this.attempts++;
            console.log(colorize(`Буквы '${ch}' нет в слове.`, 'red'));
            if (this.mode === 'survival') {
                this.roundScore = 0;
                console.log(colorize('Вы потеряли все очки раунда!', 'red'));
            }
            if (this.attempts >= this.maxAttempts) {
                console.log(colorize('Попытки закончились. Ход переходит к компьютеру.', 'yellow'));
                this.gameOver = true;
            }
            return false;
        }
    }

    guessWord(guess) {
        if (guess === this.word) {
            this.winRound();
            return true;
        } else {
            this.attempts++;
            console.log(colorize('Неверно.', 'red'));
            if (this.mode === 'survival') {
                this.roundScore = 0;
                console.log(colorize('Вы потеряли все очки раунда!', 'red'));
            }
            if (this.attempts >= this.maxAttempts) {
                this.gameOver = true;
            }
            return false;
        }
    }

    winRound() {
        console.log(colorize(`🎉 Вы отгадали слово '${this.word}'!`, 'green'));
        console.log(colorize(`Вы заработали ${this.roundScore} очков в этом раунде.`, 'yellow'));
        this.score += this.roundScore;
        if (this.mode === 'classic' || this.round >= this.totalRounds) {
            this.gameOver = true;
        } else {
            this.round++;
            this.attempts = 0;
            this.roundScore = 0;
            this.word = this.getRandomWord();
            this.displayWord = Array(this.word.length).fill('_');
            this.guessed.clear();
            this.wrong.clear();
            console.log(colorize(`Переход к раунду ${this.round}`, 'cyan'));
        }
    }

    async playTurn() {
        const rl = readline.createInterface({
            input: process.stdin,
            output: process.stdout
        });
        const question = (q) => new Promise(resolve => rl.question(q, resolve));

        while (!this.gameOver) {
            this.displayState();
            console.log('\nДействия: spin, letter <буква>, guess <слово>, buy, quit');
            const cmd = (await question('> ')).trim();
            if (cmd === 'quit') {
                console.log('Выход.');
                rl.close();
                return;
            }
            if (cmd === 'spin') {
                const sector = this.spinWheel();
                if (typeof sector.value === 'number') {
                    console.log(colorize(`Выпало ${sector.value} очков!`, 'green'));
                    const letterInput = await question('Введите букву: ');
                    if (letterInput.length !== 1) {
                        console.log(colorize('Введите одну букву.', 'red'));
                        continue;
                    }
                    const ch = letterInput.toLowerCase();
                    if (this.vowels.includes(ch)) {
                        console.log(colorize('Гласные нужно покупать (команда buy).', 'yellow'));
                        continue;
                    }
                    if (this.guessLetter(ch)) {
                        const count = (this.word.match(new RegExp(ch, 'g')) || []).length;
                        this.roundScore += count * sector.value;
                    } else {
                        console.log(colorize('Ошибка! Ход переходит к компьютеру.', 'red'));
                        this.gameOver = true;
                    }
                } else if (sector.value === 'bonus') {
                    console.log(colorize('Бонус x2! Ваши очки удваиваются!', 'magenta'));
                    this.roundScore *= 2;
                } else if (sector.value === 'bankrupt') {
                    console.log(colorize('Банкрот! Вы теряете все очки раунда!', 'red'));
                    this.roundScore = 0;
                } else if (sector.value === 'gift') {
                    console.log(colorize('Подарок! Вы получаете дополнительный ход!', 'cyan'));
                    continue;
                } else if (sector.value === 'mystery') {
                    const bonus = Math.floor(Math.random() * 251) + 50;
                    console.log(colorize(`Загадка! Вы получаете ${bonus} бонусных очков!`, 'yellow'));
                    this.roundScore += bonus;
                }
            } else if (cmd.startsWith('letter ')) {
                const ch = cmd.split(' ')[1];
                if (!ch || ch.length !== 1) {
                    console.log(colorize('Введите одну букву.', 'red'));
                    continue;
                }
                this.guessLetter(ch.toLowerCase());
            } else if (cmd.startsWith('guess ')) {
                const guess = cmd.split(' ').slice(1).join(' ');
                this.guessWord(guess);
            } else if (cmd === 'buy') {
                if (this.mode === 'classic') {
                    this.buyVowel();
                } else {
                    console.log(colorize('В этом режиме покупка гласных недоступна.', 'red'));
                }
            } else {
                console.log(colorize('Неизвестная команда.', 'red'));
            }
        }
        // Конец игры
        if (this.gameOver && !this.displayWord.includes('_')) {
            this.winRound();
        } else if (this.gameOver && this.displayWord.includes('_')) {
            console.log(colorize(`Игра окончена. Загаданное слово: ${this.word}`, 'red'));
            console.log(colorize(`Вы заработали ${this.score} очков.`, 'yellow'));
        }
        if (this.score > this.bestScore) {
            this.bestScore = this.score;
            this.saveRecord();
            console.log(colorize(`🏆 Новый рекорд: ${this.bestScore} очков!`, 'green'));
        }
        rl.close();
    }
}

async function main() {
    let mode = 'classic', theme = 'общее', word = '';
    let showStats = false, reset = false;
    const args = process.argv.slice(2);
    for (let i=0; i<args.length; i++) {
        const arg = args[i];
        if (arg === 'classic' || arg === 'survival' || arg === 'tournament') mode = arg;
        else if (arg === '-w' && i+1 < args.length) word = args[++i];
        else if (arg === '-t' && i+1 < args.length) theme = args[++i];
        else if (arg === '-s' || arg === '--stats') showStats = true;
        else if (arg === '-r' || arg === '--reset') reset = true;
        else if (arg === '-h' || arg === '--help') {
            console.log('Usage: node wheel.js [classic|survival|tournament] [-w word] [-t theme] [-s] [-r]');
            return;
        }
    }
    if (reset) {
        const f = path.join(os.homedir(), '.wheel_record.json');
        if (fs.existsSync(f)) fs.unlinkSync(f);
        console.log('Рекорды сброшены.');
        return;
    }
    if (showStats) {
        const f = path.join(os.homedir(), '.wheel_record.json');
        try {
            const data = JSON.parse(fs.readFileSync(f, 'utf8'));
            console.log(`Лучший результат: ${data.best_score || 0} очков`);
        } catch {
            console.log('Рекордов пока нет.');
        }
        return;
    }
    const game = new WheelOfFortune(mode, theme, word);
    await game.playTurn();
}

main().catch(console.error);
