#!/usr/bin/env python3
"""
Unsloth Studio API helper for training mtron models.

Usage:
    from unsloth_studio import Studio
    s = Studio('http://localhost:8882', 'unsloth', 'password')
    s.upload_dataset('path/to/dataset.jsonl')
    s.train(model='unsloth/Qwen3-8B', steps=600, gpus=[0,1])
    s.wait_for_training()
    s.export_gguf('mtron-qwen-8b')
"""
import json
import os
import time
import urllib.error
import urllib.request
import uuid


class Studio:
    def __init__(self, base_url, username, password):
        self.base = base_url.rstrip('/')
        self.token = None
        self._login(username, password)

    def _login(self, username, password):
        data = json.dumps({'username': username, 'password': password}).encode()
        r = urllib.request.Request(f'{self.base}/api/auth/login', method='POST', data=data)
        r.add_header('Content-Type', 'application/json')
        with urllib.request.urlopen(r, timeout=15) as resp:
            self.token = json.loads(resp.read())['access_token']

    def _api(self, method, path, data=None, timeout=120):
        url = f'{self.base}{path}'
        req = urllib.request.Request(url, method=method)
        req.add_header('Authorization', f'Bearer {self.token}')
        if data is not None:
            req.add_header('Content-Type', 'application/json')
            req.data = json.dumps(data).encode()
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return json.loads(resp.read())

    # ── Datasets ──

    def upload_dataset(self, filepath):
        """Upload a JSONL dataset, return stored_path."""
        boundary = '----FormBoundary' + uuid.uuid4().hex
        with open(filepath, 'rb') as f:
            data = f.read()
        body = b'--' + boundary.encode() + b'\r\n'
        body += b'Content-Disposition: form-data; name="file"; filename="dataset.jsonl"\r\n'
        body += b'Content-Type: application/jsonl\r\n\r\n'
        body += data + b'\r\n'
        body += b'--' + boundary.encode() + b'--\r\n'

        url = f'{self.base}/api/datasets/upload'
        req = urllib.request.Request(url, method='POST', data=body)
        req.add_header('Authorization', f'Bearer {self.token}')
        req.add_header('Content-Type', f'multipart/form-data; boundary={boundary}')
        with urllib.request.urlopen(req, timeout=60) as resp:
            result = json.loads(resp.read())
        return result['stored_path']

    # ── Training ──

    def train(self, *, model_name, dataset_path, steps=600, gpus=None,
              lr='0.00005', batch_size=2, grad_accum=4, lora_r=32, lora_alpha=8,
              max_seq_length=2048, scheduler='cosine', load_in_4bit=False):
        """Start a training job. Returns job_id."""
        self._api('POST', '/api/train/reset')
        payload = {
            'model_name': model_name,
            'training_type': 'LoRA/QLoRA',
            'load_in_4bit': load_in_4bit,
            'max_seq_length': max_seq_length,
            'local_datasets': [dataset_path],
            'format_type': 'alpaca',
            'num_epochs': 10,
            'learning_rate': lr,
            'batch_size': batch_size,
            'gradient_accumulation_steps': grad_accum,
            'warmup_steps': 10,
            'max_steps': steps,
            'save_steps': max(steps // 4, 50),
            'weight_decay': 0.001,
            'random_seed': 3407,
            'packing': False,
            'train_on_completions': True,
            'gradient_checkpointing': 'unsloth',
            'optim': 'adamw_8bit',
            'lr_scheduler_type': scheduler,
            'lora_r': lora_r,
            'lora_alpha': lora_alpha,
            'lora_dropout': 0,
            'target_modules': ['q_proj', 'k_proj', 'v_proj', 'o_proj',
                               'gate_proj', 'up_proj', 'down_proj'],
            'use_rslora': False,
            'use_loftq': False,
        }
        if gpus:
            payload['gpu_ids'] = gpus
        result = self._api('POST', '/api/train/start', payload)
        return result['job_id']

    def status(self):
        """Return training status dict."""
        return self._api('GET', '/api/train/status')

    def hardware(self):
        """Return per-GPU hardware info."""
        return self._api('GET', '/api/train/hardware/visible')

    def wait_for_training(self, poll_sec=15):
        """Block until training completes or errors."""
        while True:
            s = self.status()
            d = s.get('details', {})
            step = d.get('step', 0)
            total = d.get('total_steps', 0)
            loss = d.get('loss')
            phase = s.get('phase', '?')
            print(f'  [{phase}] step {step}/{total}  loss={loss}')
            if phase in ('completed', 'error'):
                print(f'  DONE: {phase} — {s.get("message", "")}')
                return s
            time.sleep(poll_sec)

    # ── Export ──

    def load_checkpoint(self, checkpoint_path):
        return self._api('POST', '/api/export/load-checkpoint',
                         {'checkpoint_path': checkpoint_path}, timeout=300)

    def export_merged(self, name, fmt='16-bit (FP16)'):
        return self._api('POST', '/api/export/export/merged',
                         {'save_directory': name, 'format_type': fmt, 'push_to_hub': False}, timeout=600)

    def export_gguf(self, name, quant='Q4_K_M'):
        return self._api('POST', '/api/export/export/gguf',
                         {'save_directory': name, 'quantization_method': quant, 'push_to_hub': False}, timeout=600)

    def list_loras(self):
        return self._api('GET', '/api/models/loras')

    def list_models(self):
        return self._api('GET', '/api/models/list')


# ── CLI ──
if __name__ == '__main__':
    import sys

    cmd = sys.argv[1] if len(sys.argv) > 1 else 'status'

    s = Studio('http://ginger.local:8882', 'unsloth',
               os.environ.get('UNSLOTH_PASSWORD', ''))

    if cmd == 'status':
        st = s.status()
        print(json.dumps(st, indent=2))
    elif cmd == 'hw':
        hw = s.hardware()
        for g in hw['devices']:
            print(f"GPU{g['index']}: {g['vram_used_gb']:.1f}/{g['vram_total_gb']:.0f}GB "
                  f"{g['gpu_utilization_pct']}% {g['temperature_c']}°C")
    elif cmd == 'loras':
        for l in s.list_loras().get('loras', []):
            print(f"{l['display_name']}  base={l['base_model']}")
    elif cmd == 'train':
        ds = s.upload_dataset(sys.argv[2])
        jid = s.train(model_name=sys.argv[3], dataset_path=ds, steps=int(sys.argv[4]) if len(sys.argv) > 4 else 600)
        print(f'Job: {jid}')
    else:
        print(f'Unknown command: {cmd}')
        print('Usage: unsloth_studio.py [status|hw|loras|train <dataset> <model> <steps>]')
