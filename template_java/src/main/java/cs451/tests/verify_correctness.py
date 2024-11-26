import os
import sys
from collections import defaultdict

def parse_logs(output_dir):
    """
    Parse logs from the output directory and return sent and delivered messages.
    """
    sent = defaultdict(list)        # process_id -> [seq_num]
    delivered = defaultdict(list)   # process_id -> [(sender_id, seq_num)]
    
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
                        sent[process_id].append(seq_num)  # Keep track of broadcasted messages
                    
                    elif tokens[0] == 'd':  # Message delivered
                        sender_id = int(tokens[1])
                        seq_num = int(tokens[2])
                        delivered[process_id].append((sender_id, seq_num))  # Record delivery

    return sent, delivered

def check_frb2(delivered):
    """
    Check for FRB2: No Duplication.
    """
    violations = []
    
    for receiver_id, delivered_messages in delivered.items():
        seen_messages = set()
        
        for msg in delivered_messages:
            if msg in seen_messages:
                violations.append(f"FRB2 Violation: Message {msg[1]} from sender {msg[0]} was delivered more than once by process {receiver_id}.")
            seen_messages.add(msg)
    
    return violations

def check_frb3(sent, delivered):
    """
    Check for FRB3: No Creation.
    """
    violations = []
    
    for receiver_id, delivered_messages in delivered.items():
        for sender_id, seq_num in delivered_messages:
            if seq_num not in sent.get(sender_id, []):
                violations.append(f"FRB3 Violation: Message {seq_num} delivered by process {receiver_id} was never broadcast by process {sender_id}.")
    
    return violations

def check_frb5(delivered):
    """
    Check for FRB5: FIFO Delivery.
    """
    violations = []
    
    for receiver_id, delivered_messages in delivered.items():
        per_sender_msgs = defaultdict(list)
        # Organize delivered messages per sender
        for sender_id, seq_num in delivered_messages:
            per_sender_msgs[sender_id].append(seq_num)
        
        for sender_id, seq_nums in per_sender_msgs.items():
            expected_seq = 1
            delivered_seq_nums = sorted(seq_nums)
            for seq_num in delivered_seq_nums:
                if seq_num != expected_seq:
                    violations.append(f"FRB5 Violation: Process {receiver_id} expected message {expected_seq} from sender {sender_id}, but received {seq_num}.")
                    expected_seq = seq_num  # Adjust expected sequence to current
                expected_seq += 1  # Expect next sequence number
    return violations

def check_frb4(delivered, num_processes):
    """
    Check for FRB4: Uniform Agreement.
    If a message is delivered by any process, it should be delivered by all correct processes.
    For testing purposes, we assume all processes are correct.
    """
    violations = []
    all_delivered_msgs = set()
    # Collect all delivered messages
    for receiver_id, delivered_messages in delivered.items():
        all_delivered_msgs.update(delivered_messages)
    
    for msg in all_delivered_msgs:
        for receiver_id in range(1, num_processes + 1):
            if msg not in delivered.get(receiver_id, []):
                violations.append(f"FRB4 Violation: Message {msg[1]} from sender {msg[0]} was delivered by some process but not by process {receiver_id}.")
    return violations

def verify_correctness(output_dir, num_processes):
    """
    Main function to verify correctness based on FRB properties.
    """
    sent, delivered = parse_logs(output_dir)
    
    # Check each rule
    frb2_violations = check_frb2(delivered)
    frb3_violations = check_frb3(sent, delivered)
    frb5_violations = check_frb5(delivered)
    frb4_violations = check_frb4(delivered, num_processes)
    
    # Output results
    if not (frb2_violations or frb3_violations or frb5_violations or frb4_violations):
        print("All checks passed. The outputs conform to the FIFO broadcast properties.")
    else:
        if frb2_violations:
            print("\nFRB2 Violations (No Duplication):")
            for v in frb2_violations:
                print(v)
        
        if frb3_violations:
            print("\nFRB3 Violations (No Creation):")
            for v in frb3_violations:
                print(v)
        
        if frb5_violations:
            print("\nFRB5 Violations (FIFO Delivery):")
            for v in frb5_violations:
                print(v)
        
        if frb4_violations:
            print("\nFRB4 Violations (Uniform Agreement):")
            for v in frb4_violations:
                print(v)

if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("Usage: python verify_correctness.py <output_directory> <num_processes>")
        sys.exit(1)

    output_directory = sys.argv[1]
    if not os.path.isdir(output_directory):
        print(f"Error: {output_directory} is not a valid directory.")
        sys.exit(1)
    
    try:
        num_processes = int(sys.argv[2])
    except ValueError:
        print("Error: num_processes must be an integer.")
        sys.exit(1)

    verify_correctness(output_directory, num_processes)
