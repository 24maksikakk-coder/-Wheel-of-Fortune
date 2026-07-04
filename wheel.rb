#!/usr/bin/env ruby
# wheel.rb
# encoding: UTF-8

require 'json'
require 'fileutils'
require 'set'

COLORS = {
  reset: "\e[0m",
  red: "\e[91m",
  green: "\e[92m",
  yellow: "\e[93m",
  blue: "\e[94m",
  magenta: "\e[95m",
  cyan: "\e[96m",
  bold: "\e[1m"
}

def colorize(text, color)
  "#{COLORS[color]}#{text}#{COLORS[:reset]}"
end

SECTORS = [
  { name: '100', color: :green, value: 100 },
  { name: '200', color: :green, value: 200 },
  { name: '300', color: :green, value: 300 },
  { name: '500', color: :green, value: 500 },
  { name: '800', color: :green, value: 800 },
  { name: '1000', color: :green, value: 1000 },
  { name: 'Бонус x2', color: :magenta, bonus: true },
  { name: 'Банкрот', color: :red, bankrupt: true },
  { name: 'Подарок', color: :cyan, gift: true },
  { name: 'Загадка', color: :yellow, mystery: true }
]

class WheelOfFortune
  attr_reader :mode, :theme, :word, :display_word, :guessed, :wrong,
              :score, :round_score, :attempts, :max_attempts, :game_over,
              :vowels, :record_file, :best_score, :round, :total_rounds

  def initialize(mode, theme, word)
    @mode = mode
    @theme = theme
    @word = word.empty? ? get_random_word : word
    @display_word = Array.new(@word.length, '_')
    @guessed = Set.new
    @wrong = Set.new
    @score = 0
    @round_score = 0
    @attempts = 0
    @max_attempts = 6
    @game_over = false
    @vowels = 'аеёиоуыэюя'
    @record_file = File.join(Dir.home, '.wheel_record.json')
    load_record
    @round = 1
    @total_rounds = (mode == 'classic') ? 1 : (mode == 'tournament') ? 3 : 1
  end

  def get_random_word
    words = {
      'животные' => ['кот','собака','тигр','слон','жираф','дельфин'],
      'растения' => ['роза','тюльпан','кактус','папоротник','дуб'],
      'страны' => ['россия','германия','франция','италия','япония'],
      'общее' => ['программирование','алгоритм','компьютер','интернет','игра']
    }
    list = words[@theme] || words['общее']
    list.sample
  end

  def load_record
    if File.exist?(@record_file)
      data = JSON.parse(File.read(@record_file))
      @best_score = data['best_score'] || 0
    else
      @best_score = 0
    end
  end

  def save_record
    File.write(@record_file, JSON.pretty_generate({ 'best_score' => @best_score }))
  end

  def display_state
    puts colorize('=' * 50, :bold)
    puts colorize("🎡 Колесо фортуны  |  Раунд #{@round}/#{@total_rounds}", :bold)
    puts colorize("Слово: #{@display_word.join(' ')}", :blue)
    puts colorize("Тема: #{@theme}", :cyan)
    puts colorize("Очки: #{@score}  |  Очки раунда: #{@round_score}", :yellow)
    puts colorize("Открытые буквы: #{@guessed.to_a.join(', ') || 'нет'}", :green)
    puts colorize("Ошибочные буквы: #{@wrong.to_a.join(', ') || 'нет'}", :red)
    puts colorize('=' * 50, :bold)
  end

  def spin_wheel
    puts colorize('🎡 Колесо вращается...', :bold)
    5.times do
      sector = SECTORS.sample
      print "\r#{colorize(sector[:name], sector[:color])}"
      sleep 0.2
    end
    sector = SECTORS.sample
    print "\r#{colorize(sector[:name], sector[:color])}\n"
    sector
  end

  def buy_vowel
    if @score < 100
      puts colorize('Недостаточно очков для покупки гласной (нужно 100).', :red)
      return false
    end
    vowels_list = @vowels.chars.reject { |v| @guessed.include?(v) }
    if vowels_list.empty?
      puts colorize('Все гласные уже открыты.', :yellow)
      return false
    end
    v = vowels_list.first
    @score -= 100
    @guessed.add(v)
    reveal_letter(v)
    puts colorize("Вы купили гласную '#{v}' за 100 очков.", :green)
    true
  end

  def reveal_letter(ch)
    count = 0
    @word.chars.each_with_index do |c, i|
      if c == ch
        @display_word[i] = ch
        count += 1
      end
    end
    count
  end

  def guess_letter(ch)
    if @guessed.include?(ch) || @wrong.include?(ch)
      puts colorize('Вы уже называли эту букву.', :yellow)
      return false
    end
    if @vowels.include?(ch)
      puts colorize('Гласные буквы можно только купить (команда buy).', :yellow)
      return false
    end
    if @word.include?(ch)
      @guessed.add(ch)
      count = reveal_letter(ch)
      @round_score += count * 50
      puts colorize("Буква '#{ch}' есть в слове! (#{count} раз)", :green)
      unless @display_word.include?('_')
        win_round
      end
      true
    else
      @wrong.add(ch)
      @attempts += 1
      puts colorize("Буквы '#{ch}' нет в слове.", :red)
      if @mode == 'survival'
        @round_score = 0
        puts colorize('Вы потеряли все очки раунда!', :red)
      end
      if @attempts >= @max_attempts
        puts colorize('Попытки закончились. Ход переходит к компьютеру.', :yellow)
        @game_over = true
      end
      false
    end
  end

  def guess_word(guess)
    if guess == @word
      win_round
      true
    else
      @attempts += 1
      puts colorize('Неверно.', :red)
      if @mode == 'survival'
        @round_score = 0
        puts colorize('Вы потеряли все очки раунда!', :red)
      end
      if @attempts >= @max_attempts
        @game_over = true
      end
      false
    end
  end

  def win_round
    puts colorize("🎉 Вы отгадали слово '#{@word}'!", :green)
    puts colorize("Вы заработали #{@round_score} очков в этом раунде.", :yellow)
    @score += @round_score
    if @mode == 'classic' || @round >= @total_rounds
      @game_over = true
    else
      @round += 1
      @attempts = 0
      @round_score = 0
      @word = get_random_word
      @display_word = Array.new(@word.length, '_')
      @guessed.clear
      @wrong.clear
      puts colorize("Переход к раунду #{@round}", :cyan)
    end
  end

  def play_turn
    until @game_over
      display_state
      puts "\nДействия: spin, letter <буква>, guess <слово>, buy, quit"
      print '> '
      cmd = gets.chomp.strip
      case cmd
      when 'quit'
        puts 'Выход.'
        return
      when 'spin'
        sector = spin_wheel
        if sector[:value]
          puts colorize("Выпало #{sector[:value]} очков!", :green)
          print 'Введите букву: '
          letter = gets.chomp.strip
          if letter.length != 1 || !letter.match?(/[а-яА-Я]/)
            puts colorize('Введите одну букву.', :red)
            next
          end
          ch = letter.downcase
          if @vowels.include?(ch)
            puts colorize('Гласные нужно покупать (команда buy).', :yellow)
            next
          end
          if guess_letter(ch)
            count = @word.count(ch)
            @round_score += count * sector[:value]
          else
            puts colorize('Ошибка! Ход переходит к компьютеру.', :red)
            @game_over = true
          end
        elsif sector[:bonus]
          puts colorize('Бонус x2! Ваши очки удваиваются!', :magenta)
          @round_score *= 2
        elsif sector[:bankrupt]
          puts colorize('Банкрот! Вы теряете все очки раунда!', :red)
          @round_score = 0
        elsif sector[:gift]
          puts colorize('Подарок! Вы получаете дополнительный ход!', :cyan)
          next
        elsif sector[:mystery]
          bonus = rand(50..300)
          puts colorize("Загадка! Вы получаете #{bonus} бонусных очков!", :yellow)
          @round_score += bonus
        end
      when /^letter /
        ch = cmd.split[1]
        if ch.nil? || ch.length != 1
          puts colorize('Введите одну букву.', :red)
          next
        end
        guess_letter(ch.downcase)
      when /^guess /
        guess = cmd.split(' ', 2)[1]
        guess_word(guess)
      when 'buy'
        if @mode == 'classic'
          buy_vowel
        else
          puts colorize('В этом режиме покупка гласных недоступна.', :red)
        end
      else
        puts colorize('Неизвестная команда.', :red)
      end
    end
    if @game_over && !@display_word.include?('_')
      win_round
    elsif @game_over && @display_word.include?('_')
      puts colorize("Игра окончена. Загаданное слово: #{@word}", :red)
      puts colorize("Вы заработали #{@score} очков.", :yellow)
    end
    if @score > @best_score
      @best_score = @score
      save_record
      puts colorize("🏆 Новый рекорд: #{@best_score} очков!", :green)
    end
  end
end

def main
  mode = 'classic'
  theme = 'общее'
  word = ''
  show_stats = false
  reset = false
  i = 0
  while i < ARGV.size
    arg = ARGV[i]
    case arg
    when 'classic', 'survival', 'tournament'
      mode = arg
    when '-w'
      word = ARGV[i+1] if i+1 < ARGV.size
      i += 1
    when '-t'
      theme = ARGV[i+1] if i+1 < ARGV.size
      i += 1
    when '-s', '--stats'
      show_stats = true
    when '-r', '--reset'
      reset = true
    when '-h', '--help'
      puts "Usage: ruby wheel.rb [classic|survival|tournament] [-w word] [-t theme] [-s] [-r]"
      return
    end
    i += 1
  end
  if reset
    f = File.join(Dir.home, '.wheel_record.json')
    File.delete(f) if File.exist?(f)
    puts 'Рекорды сброшены.'
    return
  end
  if show_stats
    f = File.join(Dir.home, '.wheel_record.json')
    if File.exist?(f)
      data = JSON.parse(File.read(f))
      puts "Лучший результат: #{data['best_score'] || 0} очков"
    else
      puts 'Рекордов пока нет.'
    end
    return
  end
  game = WheelOfFortune.new(mode, theme, word)
  game.play_turn
end

main if __FILE__ == $0
