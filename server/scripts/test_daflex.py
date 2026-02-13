import requests
import json
import urllib.parse
from pprint import pprint

def check_daflex(word):
    url = f"https://cental.uclouvain.be/cefrlex/cefrlex/daflex/autocomplete/TreeTagger%20-%20German/{urllib.parse.quote(word)}/"
    
    headers = {
        'accept': '*/*',
        'accept-language': 'en-US,en;q=0.9',
        'priority': 'u=1, i',
        'referer': 'https://cental.uclouvain.be/cefrlex/daflex/search/',
        'user-agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36',
        'sec-ch-ua': '"Not(A:Brand";v="8", "Chromium";v="144", "Google Chrome";v="144"',
        'sec-ch-ua-mobile': '?0',
        'sec-ch-ua-platform': '"Windows"',
        'sec-fetch-dest': 'empty',
        'sec-fetch-mode': 'cors',
        'sec-fetch-site': 'same-origin'
    }

    try:
        response = requests.get(url, headers=headers)
        if response.status_code == 200:
            print(f"--- Response for '{word}' ---")
            try:
                data = response.json()
                pprint(data, indent=2)
            except:
                print("Response text:", response.text)
        else:
            print(f"Error {response.status_code}: {response.text}")
    except Exception as e:
        print(f"Request failed: {e}")

if __name__ == "__main__":
    check_daflex("mann")
    check_daflex("gehen")
