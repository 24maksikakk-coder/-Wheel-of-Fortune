# wheel.py
#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import sys
import os
import json
import random
import time
from pathlib import Path

# ANSI-цвета
COLORS = {
    'reset': '\033[0m',
    'red': '\033[91m',
    'green': '\033[92m',
    'yellow': '\033[93m',
    'blue': '\033[94m',
    'magenta': '\033[95m',
    'cyan': '\033[96m',
    'bold': '\033[1m'
}

def colorize(text, color):
    return f"{COLORS.get(color, '')}{text}{COLORS['reset']}"

# Сектора колеса (название, цвет, множитель/действие)
SECTORS = [
    ('100', 'green', 100),
    ('200', 'green', 200),
    ('300', 'green', 300),
    ('500', 'green', 500),
    ('800', 'green', 800),
    ('1000', 'green', 1000),
    ('Бонус x2', 'magenta', 'bonus'),
    ('Банкрот', 'red', 'bankrupt'),
    ('Подарок', 'cyan', 'gift'),
    ('Загадка', 'yellow', 'mystery')
]

class WheelOfFortune:
    def __init__(self, mode='classic', theme='общее', word=None):
        self.mode = mode
        self.theme = theme
        self.word = word or self.get_random_word()
        self.display_word = ['_'] * len(self.word)
        self.guessed_letters = set()
        self.wrong_letters = set()
        self.score = 0
        self.round_score = 0
        self.attempts = 0
        self.max_attempts = 6
        self.game_over = False
        self.vowels = 'аеёиоуыэюя'
        self.record_file = Path.home() / '.wheel_record.json'
        self.load_record()
        self.round = 1
        self.total_rounds = 1 if mode == 'classic' else 3 if mode == 'tournament' else 1

    def get_random_word(self):
        words = {
            'животные': ['кот', 'собака', 'тигр', 'слон', 'жираф', 'дельфин'],
            'растения': ['роза', 'тюльпан', 'кактус', 'папоротник', 'дуб'],
            'страны': ['россия', 'германия', 'франция', 'италия', 'япония'],
            'общее': ['программирование', 'алгоритм', 'компьютер', 'интернет', 'игра']
        }
        words_list = words.get(self.theme, words['общее'])
        return random.choice(words_list)

    def load_record(self):
        if self.record_file.exists():
            with open(self.record_file, 'r') as f:
                data = json.load(f)
                self.best_score = data.get('best_score', 0)
        else:
            self.best_score = 0

    def save_record(self):
        with open(self.record_file, 'w') as f:
            json.dump({'best_score': self.best_score}, f)

    def display_state(self):
        print(colorize("=" * 50, 'bold'))
        print(colorize(f"🎡 Колесо фортуны  |  Раунд {self.round}/{self.total_rounds}", 'bold'))
        print(colorize(f"Слово: {' '.join(self.display_word)}", 'blue'))
        print(colorize(f"Тема: {self.theme}", 'cyan'))
        print(colorize(f"Очки: {self.score}  |  Очки раунда: {self.round_score}", 'yellow'))
        print(colorize(f"Открытые буквы: {', '.join(sorted(self.guessed_letters)) if self.guessed_letters else 'нет'}", 'green'))
        print(colorize(f"Ошибочные буквы: {', '.join(sorted(self.wrong_letters)) if self.wrong_letters else 'нет'}", 'red'))
        print(colorize("=" * 50, 'bold'))

    def spin_wheel(self):
        print(colorize("🎡 Колесо вращается...", 'bold'))
        for i in range(5):
            print(f"\r{random.choice(SECTORS)[0]}", end='')
            time.sleep(0.2)
        sector = random.choice(SECTORS)
        print(f"\r{colorize(sector[0], sector[1])}")
        return sector

    def buy_vowel(self):
        if self.score < 100:
            print(colorize("Недостаточно очков для покупки гласной (нужно 100).", 'red'))
            return False
        vowels = [v for v in self.vowels if v not in self.guessed_letters]
        if not vowels:
            print(colorize("Все гласные уже открыты.", 'yellow'))
            return False
        # Покупаем первую доступную гласную
        v = vowels[0]
        self.score -= 100
        self.guessed_letters.add(v)
        self.reveal_letter(v)
        print(colorize(f"Вы купили гласную '{v}' за 100 очков.", 'green'))
        return True

    def reveal_letter(self, letter):
        count = 0
        for i, ch in enumerate(self.word):
            if ch == letter:
                self.display_word[i] = ch
                count += 1
        return count

    def guess_letter(self, letter):
        if letter in self.guessed_letters or letter in self.wrong_letters:
            print(colorize("Вы уже называли эту букву.", 'yellow'))
            return False
        if letter in self.vowels:
            # Гласные можно только купить
            print(colorize("Гласные буквы можно только купить (команда buy).", 'yellow'))
            return False
        if letter in self.word:
            self.guessed_letters.add(letter)
            count = self.reveal_letter(letter)
            self.round_score += count * 50  # базовый бонус за каждую букву
            print(colorize(f"Буква '{letter}' есть в слове! ({count} раз)", 'green'))
            # Проверяем, отгадано ли слово
            if '_' not in self.display_word:
                self.win_round()
            return True
        else:
            self.wrong_letters.add(letter)
            self.attempts += 1
            print(colorize(f"Буквы '{letter}' нет в слове.", 'red'))
            if self.mode == 'survival':
                self.round_score = 0
                print(colorize("Вы потеряли все очки раунда!", 'red'))
            if self.attempts >= self.max_attempts:
                print(colorize("Попытки закончились. Ход переходит к компьютеру.", 'yellow'))
                self.game_over = True
            return False

    def guess_word(self, guess):
        if guess.lower() == self.word:
            self.win_round()
            return True
        else:
            self.attempts += 1
            print(colorize("Неверно.", 'red'))
            if self.mode == 'survival':
                self.round_score = 0
                print(colorize("Вы потеряли все очки раунда!", 'red'))
            if self.attempts >= self.max_attempts:
                self.game_over = True
            return False

    def win_round(self):
        print(colorize(f"🎉 Вы отгадали слово '{self.word}'!", 'green'))
        print(colorize(f"Вы заработали {self.round_score} очков в этом раунде.", 'yellow'))
        self.score += self.round_score
        if self.mode == 'classic' or self.round >= self.total_rounds:
            self.game_over = True
        else:
            self.round += 1
            self.attempts = 0
            self.round_score = 0
            self.display_word = ['_'] * len(self.word)
            self.guessed_letters.clear()
            self.wrong_letters.clear()
            self.word = self.get_random_word()
            print(colorize(f"Переход к раунду {self.round}", 'cyan'))

    def play_turn(self):
        self.display_state()
        while not self.game_over:
            if self.mode == 'classic':
                print("\nДействия: spin, letter <буква>, guess <слово>, buy, quit")
            else:
                print("\nДействия: spin, letter <буква>, guess <слово>, quit")
            cmd = input("> ").strip().lower()
            if cmd == 'quit':
                print("Выход.")
                sys.exit(0)
            if cmd == 'spin':
                sector = self.spin_wheel()
                val = sector[2]
                if isinstance(val, int):
                    # Денежный сектор
                    print(colorize(f"Выпало {val} очков!", 'green'))
                    letter = input("Введите букву: ").strip().lower()
                    if letter in self.vowels:
                        print(colorize("Гласные нужно покупать (команда buy).", 'yellow'))
                        continue
                    if self.guess_letter(letter):
                        count = self.word.count(letter)
                        self.round_score += count * val
                    else:
                        # ошибка – ход переходит к компьютеру (упрощённо)
                        print(colorize("Ошибка! Ход переходит к компьютеру.", 'red'))
                        self.game_over = True
                elif val == 'bonus':
                    print(colorize("Бонус x2! Ваши очки удваиваются!", 'magenta'))
                    self.round_score *= 2
                elif val == 'bankrupt':
                    print(colorize("Банкрот! Вы теряете все очки раунда!", 'red'))
                    self.round_score = 0
                elif val == 'gift':
                    print(colorize("Подарок! Вы получаете дополнительный ход!", 'cyan'))
                    continue
                elif val == 'mystery':
                    bonus = random.randint(50, 300)
                    print(colorize(f"Загадка! Вы получаете {bonus} бонусных очков!", 'yellow'))
                    self.round_score += bonus
            elif cmd.startswith('letter '):
                letter = cmd.split()[1]
                if len(letter) != 1 or not letter.isalpha():
                    print("Введите одну букву.")
                    continue
                self.guess_letter(letter)
            elif cmd.startswith('guess '):
                guess = cmd.split(' ', 1)[1]
                self.guess_word(guess)
            elif cmd == 'buy':
                if self.mode == 'classic':
                    self.buy_vowel()
                else:
                    print("В этом режиме покупка гласных недоступна.")
            else:
                print("Неизвестная команда.")

        # Конец раунда/игры
        if self.game_over and '_' not in self.display_word:
            self.win_round()
        elif self.game_over and '_' in self.display_word:
            print(colorize(f"Игра окончена. Загаданное слово: {self.word}", 'red'))
            print(colorize(f"Вы заработали {self.score} очков.", 'yellow'))
        # Обновление рекорда
        if self.score > self.best_score:
            self.best_score = self.score
            self.save_record()
            print(colorize(f"🏆 Новый рекорд: {self.best_score} очков!", 'green'))

def main():
    mode = 'classic'
    word = None
    theme = 'общее'
    show_stats = False
    reset = False
    args = sys.argv[1:]
    i = 0
    while i < len(args):
        arg = args[i]
        if arg in ['classic', 'survival', 'tournament']:
            mode = arg
        elif arg == '-w' and i+1 < len(args):
            word = args[i+1]
            i += 1
        elif arg == '-t' and i+1 < len(args):
            theme = args[i+1]
            i += 1
        elif arg == '-s' or arg == '--stats':
            show_stats = True
        elif arg == '-r' or arg == '--reset':
            reset = True
        elif arg == '-h' or arg == '--help':
            print("Usage: wheel.py [classic|survival|tournament] [-w word] [-t theme] [-s] [-r]")
            return
        i += 1
    if reset:
        record_file = Path.home() / '.wheel_record.json'
        if record_file.exists():
            record_file.unlink()
        print("Рекорды сброшены.")
        return
    if show_stats:
        record_file = Path.home() / '.wheel_record.json'
        if record_file.exists():
            with open(record_file, 'r') as f:
                data = json.load(f)
                print(f"Лучший результат: {data.get('best_score', 0)} очков")
        else:
            print("Рекордов пока нет.")
        return
    game = WheelOfFortune(mode, theme, word)
    game.play_turn()

if __name__ == '__main__':
    try:
        main()
    except KeyboardInterrupt:
        print(colorize("\nИгра прервана.", 'yellow'))
        sys.exit(0)
