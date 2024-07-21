import os
from telegram import Update
from telegram.ext import Application, CommandHandler, MessageHandler, filters, ContextTypes

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

async def start(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    """Отправляет приветственное сообщение когда пользователь отправляет команду /start"""
    await update.message.reply_text('Привет! Я бот для генерации промптов для GPT-4. Отправь мне сообщение, и я сгенерирую ответ.')

async def handle_message(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    """Получает сообщение от пользователя и отвечает, используя GPT-4"""
    user_text = update.message.text
    response = openai.Completion.create(
        engine="gpt-4",  # Убедитесь, что используете правильное имя модели
        prompt=user_text,
        max_tokens=150
    )
    bot_reply = response.choices[0].text.strip()
    await update.message.reply_text(bot_reply)

def main() -> None:
    """Запускает бота"""
    # Создание объекта Application и передача ему токена бота
    application = Application.builder().token(TELEGRAM_BOT_TOKEN).build()
    
    # Создание диспетчера для обработки команд и сообщений
    application.add_handler(CommandHandler("start", start))
    
    # Обработчик всех текстовых сообщений
    application.add_handler(MessageHandler(filters.TEXT & ~filters.COMMAND, handle_message))

    # Запуск бота
    application.run_polling()

if __name__ == '__main__':
    main()
