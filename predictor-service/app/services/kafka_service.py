import json
import logging
import asyncio
from confluent_kafka import Consumer, Producer, KafkaError
from app.core.config import settings
from app.services.forecasting_service import forecast_service

logger = logging.getLogger(__name__)

class KafkaManager:
    def __init__(self):
        self.consumer_conf = {
            'bootstrap.servers': settings.KAFKA_BOOTSTRAP_SERVERS,
            'group.id': 'predictor-consumer-group',
            'auto.offset.reset': 'earliest'
        }
        self.producer_conf = {
            'bootstrap.servers': settings.KAFKA_BOOTSTRAP_SERVERS
        }
        self.consumer = None
        self.producer = None
        self.running = False

    async def start(self):
        self.consumer = Consumer(self.consumer_conf)
        self.producer = Producer(self.producer_conf)
        self.consumer.subscribe([settings.RECIPE_COOKING_TOPIC])
        self.running = True
        
        while self.running:
            msg = self.consumer.poll(1.0)
            if msg is None:
                await asyncio.sleep(0.1)
                continue
            if msg.error():
                if msg.error().code() == KafkaError._PARTITION_EOF:
                    continue
                else:
                    logger.error(f"Kafka error: {msg.error()}")
                    break
            
            try:
                payload = json.loads(msg.value().decode('utf-8'))
                logger.info(f"Received cooking event: {payload}")
                
                results = await forecast_service.process_event(payload)
                
                if results:
                    for result in results:
                        self.send_forecast_update(result)
                    
            except Exception as e:
                logger.error(f"Error processing message: {e}")

    def send_forecast_update(self, data):
        try:
            self.producer.produce(
                settings.FORECAST_UPDATES_TOPIC,
                key=str(data.get("productId")),
                value=json.dumps(data).encode('utf-8')
            )
            self.producer.flush()
            logger.info(f"Sent forecast update for product {data.get('productId')}")
        except Exception as e:
            logger.error(f"Error producing forecast update: {e}")

    async def stop(self):
        self.running = False
        if self.consumer:
            self.consumer.close()

kafka_manager = KafkaManager()
