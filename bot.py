import os
from telegram.ext import Updater, CommandHandler, MessageHandler, Filters
import openai
import logging

# Получаем токены из переменных окружения
TELEGRAM_BOT_TOKEN = os.getenv('TELEGRAM_BOT_TOKEN')
OPENAI_API_KEY = os.getenv('OPENAI_API_KEY')

openai.api_key = OPENAI_API_KEY

# Настройка логирования
logging.basicConfig(format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
                    level=logging.INFO)
logger = logging.getLogger(__name__)

def start(update, context):
    update.message.reply_text('Привет! Я бот для генерации промптов для GPT-4. Отправь мне сообщение, и я сгенерирую ответ.')

def handle_message(update, context):
    user_text = update.message.text
    response = openai.Completion.create(
        engine="text-davinci-003",  # Напоминаем, что это псевдоним для GPT-4
        prompt=user_text,
        max_tokens=150
    )
    bot_reply = response.choices[0].text.strip()
    update.message.reply_text(bot_reply)

def main():
    # Создание объекта Updater и передача ему токена бота
    updater = Updater(TELEGRAM_BOT_TOKEN, use_context=True)
    
    # Создание диспетчера для обработки команд и сообщений
    dp = updater.dispatcher

    # Обработчик команд "/start"
    dp.add_handler(CommandHandler("start", start))
    
    # Обработчик всех текстовых сообщений
    dp.add_handler(MessageHandler(Filters.text & ~Filters.command, handle_message))

    # Запуск бота
    updater.start_polling()

    # Бот будет работать до остановки с клавиатуры (Ctrl+C)
    updater.idle()

if __name__ == '__main__':
    main()
