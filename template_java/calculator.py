import os

def count(parent_dir):
    for root, _, files in os.walk(parent_dir):
        number_of_d = len([file for file in files if file.endswith(".output")])
        for file in files:
            if file.endswith(".output"):
                with open(os.path.join(root, file), 'r') as f:
                    content = f.read()
                    print(f"\n{file}")
                    print("b  :", content.count("b "))
                    for i in range(1, number_of_d+1):
                        print(f"d {i}:", content.count(f"d {i}"))

count('./../example/output/')