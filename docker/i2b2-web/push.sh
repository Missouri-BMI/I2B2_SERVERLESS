#!/bin/bash

# This script demonstrates how to handle a multi-character option.

# Initialize a variable for the parameter
paramValue=""

# Manually parse options
while [[ $# -gt 0 ]]; do
    case "$1" in
        -env)
            if [[ -n "$2" ]]; then
                paramValue="$2"
                shift 2
            else
                echo "Option -$1 requires an argument." >&2
                exit 1
            fi
            ;;
        *)
            echo "Invalid option: $1" >&2
            exit 1
            ;;
    esac
done

# Check if the parameter was provided
if [ -z "$paramValue" ]; then
    echo "Usage: $0 -env <parameter>"
    exit 1
fi

# Add logic based on the parameter value
if [ "$paramValue" == "dev" ]; then

docker push 500206249851.dkr.ecr.us-east-2.amazonaws.com/i2b2-web:latest

elif [ "$paramValue" == "prod" ]; then

docker push 063312575449.dkr.ecr.us-east-2.amazonaws.com/i2b2-web:latest

else
    echo "specify -env=[dev/prod]"
    # Add commands for other values here
fi







