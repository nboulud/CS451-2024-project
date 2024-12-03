import os
from collections import defaultdict
from tqdm import tqdm
from count import count
import time

def parse_logs(output_dir):
    """
    Parse logs from the output directory and return sent and delivered messages.
    """
    broadcast = defaultdict(list)        # sender_id -> [(receiver_id, seq_num)]
    delivered = defaultdict(list)   # receiver_id -> [(sender_id, seq_num)]
    
    # Iterate over output files in the directory
    for filename in os.listdir(output_dir):
        if filename.endswith(".output"):
            process_id = int(filename.split('.')[0])
            file_path = os.path.join(output_dir, filename)
            
            with open(file_path, 'r') as f:
                for line in f:
                    tokens = line.strip().split()
                    
                    if tokens[0] == 'b':  # Message broadcasted (sent)
                        seq_num = int(tokens[1])
                        broadcast[process_id].append((None, seq_num))  # Keep track of all sent messages
                
                    elif tokens[0] == 'd':  # Message delivered
                        sender_id = int(tokens[1])
                        seq_num = int(tokens[2])
                        delivered[process_id].append((sender_id, seq_num))  # Record delivery

    return broadcast, delivered


def check_duplicates(broadcast, delivered) -> bool:

    error = False

    print("\nChecking for duplicate broadcasts:")
    for host in broadcast.keys():
            setb = set(broadcast[host])
            listb = list(broadcast[host])
            diff = len(listb) - len(setb)
            if diff > 0:
                print(f"    ⛔ Duplication Violation: {diff} duplicate(s) broadcasts found for {host}!")
                error = True
            else :
                print(f"    🌱 No duplicate broadcasts found for {host}")

    print("\nChecking for duplicate delivered:")
    for host in delivered.keys():
        setb = set(delivered[host])
        listb = list(delivered[host])
        diff = len(listb) - len(setb)
        if diff > 0:
            print(f"    ⛔ Duplication Violation: {diff} duplicate(s) delivered found for {host}!")
            error = True
        else :
            print(f"    🌱 No duplicate delivered for {host}")
    
    return error


def check_creations(broadcast, delivered) -> bool:
    """
    Check for PL3: No Creation.
    """
    print("\nChecking for creations:")

    error = False
    
    violations = []

    for receiver_id, delivered_messages in delivered.items():
        for sender_id, seq_num in tqdm(delivered_messages, desc=f"       process {receiver_id}", leave=False):
            if (None, seq_num) not in broadcast.get(sender_id, []):
                violations.append(f"Creation Violation: Message {seq_num} delivered by process {receiver_id} was never broadcast by process {sender_id}.")
        
        if len(violations) == 0:
            print(f"    🌱 No creation found for process {receiver_id}")
        else:
            max_print = 3 # Put to -1 to print all violations
            for violation in violations:
                if max_print == 0:
                    print("    ⛔ ...\n")
                    break
                print(f"    ⛔ {violation}")
                errors = True
                max_print -= 1
            violations = []

    return error


def check_fifo_ordering(broadcast, delivered) -> bool:
    """
    Check for FIFO ordering.
    """
    print("\nChecking for FIFO ordering:")
    
    error = False

    violations = []
        
    for receiver_id, delivered_messages in delivered.items():
        delivered_by_sender = defaultdict(list)
        for sender_id, seq_num in delivered_messages:
            delivered_by_sender[sender_id].append(seq_num)
            
        for sender_id, seq_nums in delivered_by_sender.items():
            for i in range(1, len(seq_nums)):
                if seq_nums[i] != seq_nums[i-1] + 1:
                    violations.append(f"FIFO Violation: 'd {sender_id} {seq_nums[i-1]}' before 'd {sender_id} {seq_nums[i]}' for process {receiver_id}.")

        
        if len(violations) == 0:
            print(f"    🌱 FIFO ordering maintained for process {receiver_id}")
        else:
            max_print = 3 # Put to -1 to print all violations
            for violation in violations:
                if max_print == 0:
                    print("    ⛔ ...\n")
                    break
                print(f"    ⛔ {violation}")
                error = True
                max_print -= 1
            violations = []

    return error

def check_correctness(parent_dir):

    count(parent_dir)

    broadcast, delivered = parse_logs(parent_dir)

    # Volontary Add a wrong data
    add_wrong_data: bool = False
    if (add_wrong_data):
        print("🚨🚨🚨 WARNING: Manually adding a wrong data 🚨🚨🚨")
        broadcast[1].append(broadcast[1][0])
        delivered[3].append(delivered[3][0])


    error1 = check_duplicates(broadcast, delivered)

    error2 = check_creations(broadcast, delivered)

    error3 = check_fifo_ordering(broadcast, delivered)

    joke_mod = True

    if (not error1 and not error2 and not error3):
        print("\033[3m")
        print("\n🎉 Hurray All correctness properties satisfied!")
        if (joke_mod): little_j_1()
        print("\033[0m")
    else:
        print("\033[3m")
        print("\n\n😫 Ooops! Some properties got violated!")
        if (joke_mod): little_j_2()
        print("\nTips: Don't look at FIFO errors if you have duplication or creation errors, fix them first!")
        print("\033[0m")

def little_j_1():
    time.sleep(3)
    print("\nYou are a real beast in Distributed Algorithms. 🦁")
    time.sleep(3)
    print("\nYou are the king of the jungle, proud of you ma boi! 🌴")

def little_j_2():
    time.sleep(3)
    print("\nYou need to work on your Distributed Algorithms skills 🐢")
    time.sleep(4)
    print("\nBut I am sure you will get there 🚀, I trust in you buddy, don't give up!")
    time.sleep(6)
    print("\nI see your face man, you feel depressed, don't worry, I am here for you!")
    time.sleep(4)
    print("\nI will help you to fix the issues, let's do it together!")
    time.sleep(4)
    print("\033[0m")
    print("\nStep 1: Navigate to chat.openai.com")
    print("\033[3m")
    time.sleep(7)
    print("\nDid you do it? 🤔")
    time.sleep(4)
    print("\nI'm sure you already did it during this project, didn't you? 😏")
    time.sleep(4)
    print("\nOkay, I'm kidding, I'm kidding, but I can't help you, I'm just a program, not a stupid AI 😂")
    time.sleep(4)
    print("\nOkay enough, I am wasting your time, time to work!")
    time.sleep(3)
    print("\nGood Luck my friend! 🍀")

check_correctness('./../example/output/')


