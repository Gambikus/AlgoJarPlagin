FROM python:3.12-slim
WORKDIR /app
RUN pip install --no-cache-dir requests urllib3
COPY spot-node/spot_node.py .
CMD ["python", "-u", "spot_node.py"]
