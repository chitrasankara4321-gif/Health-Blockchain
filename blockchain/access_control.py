"""
Blockchain-based Access Control for HealthChain
Implements smart contract-like functionality for healthcare data access
"""

import json
import hashlib
import datetime
import os
from cryptography.fernet import Fernet
import base64

class BlockchainAccessControl:
    def __init__(self):
        self.chain_file = "blockchain/access_chain.json"
        self.contracts_file = "blockchain/access_contracts.json"
        self.init_blockchain()
    
    def init_blockchain(self):
        """Initialize blockchain with genesis block"""
        os.makedirs("blockchain", exist_ok=True)
        
        if not os.path.exists(self.chain_file):
            genesis_block = {
                "index": 0,
                "timestamp": datetime.datetime.now().isoformat(),
                "access_grant": {
                    "doctor_id": "genesis",
                    "patient_id": "genesis",
                    "file_hash": "genesis",
                    "permissions": ["read", "write"],
                    "expires": None
                },
                "previous_hash": "0",
                "hash": self.calculate_hash(0, datetime.datetime.now().isoformat(), "genesis")
            }
            
            with open(self.chain_file, 'w') as f:
                json.dump([genesis_block], f, indent=2)
        
        if not os.path.exists(self.contracts_file):
            contracts = {
                "patient_consent": {
                    "description": "Patient grants access to doctor",
                    "terms": {
                        "duration_max": "90_days",
                        "revocable": True,
                        "audit_required": True,
                        "emergency_override": True
                    }
                },
                "doctor_access": {
                    "description": "Doctor requests patient data access",
                    "terms": {
                        "justification_required": True,
                        "department_verification": True,
                        "license_valid": True,
                        "audit_trail": True
                    }
                },
                "emergency_access": {
                    "description": "Emergency break-glass access",
                    "terms": {
                        "time_limit": "1_hour",
                        "admin_notification": True,
                        "post_review": True,
                        "auto_revoke": True
                    }
                }
            }
            
            with open(self.contracts_file, 'w') as f:
                json.dump(contracts, f, indent=2)
    
    def calculate_hash(self, index, timestamp, access_grant):
        """Calculate block hash"""
        block_string = f"{index}{timestamp}{json.dumps(access_grant, sort_keys=True)}"
        return hashlib.sha256(block_string.encode()).hexdigest()
    
    def get_latest_block(self):
        """Get the latest block in the chain"""
        with open(self.chain_file, 'r') as f:
            chain = json.load(f)
        return chain[-1]
    
    def add_access_grant(self, doctor_id, patient_id, file_hash, permissions, duration_days=None):
        """Add a new access grant to the blockchain"""
        chain = self.load_chain()
        latest_block = chain[-1]
        
        # Calculate expiration
        expires = None
        if duration_days:
            expires = (datetime.datetime.now() + datetime.timedelta(days=duration_days)).isoformat()
        
        access_grant = {
            "doctor_id": doctor_id,
            "patient_id": patient_id,
            "file_hash": file_hash,
            "permissions": permissions,
            "granted_at": datetime.datetime.now().isoformat(),
            "expires": expires,
            "status": "active"
        }
        
        new_block = {
            "index": len(chain),
            "timestamp": datetime.datetime.now().isoformat(),
            "access_grant": access_grant,
            "previous_hash": latest_block["hash"],
            "hash": self.calculate_hash(len(chain), datetime.datetime.now().isoformat(), access_grant)
        }
        
        chain.append(new_block)
        self.save_chain(chain)
        
        return new_block
    
    def verify_access(self, doctor_id, patient_id, file_hash):
        """Verify if doctor has access to patient file"""
        chain = self.load_chain()
        
        for block in reversed(chain):
            grant = block["access_grant"]
            
            if (grant["doctor_id"] == doctor_id and 
                grant["patient_id"] == patient_id and 
                grant["file_hash"] == file_hash and
                grant["status"] == "active"):
                
                # Check if expired
                if grant["expires"]:
                    expires = datetime.datetime.fromisoformat(grant["expires"])
                    if datetime.datetime.now() > expires:
                        return False, "Access expired"
                
                return True, "Access granted"
        
        return False, "No access grant found"
    
    def revoke_access(self, doctor_id, patient_id, file_hash):
        """Revoke access grant"""
        chain = self.load_chain()
        
        for block in chain:
            grant = block["access_grant"]
            
            if (grant["doctor_id"] == doctor_id and 
                grant["patient_id"] == patient_id and 
                grant["file_hash"] == file_hash):
                
                grant["status"] = "revoked"
                grant["revoked_at"] = datetime.datetime.now().isoformat()
                break
        
        self.save_chain(chain)
        return True
    
    def get_access_history(self, patient_id=None, doctor_id=None):
        """Get access history for patient or doctor"""
        chain = self.load_chain()
        history = []
        
        for block in chain:
            grant = block["access_grant"]
            
            if (patient_id and grant["patient_id"] == patient_id) or \
               (doctor_id and grant["doctor_id"] == doctor_id):
                history.append({
                    "block_index": block["index"],
                    "timestamp": block["timestamp"],
                    "hash": block["hash"],
                    "grant": grant
                })
        
        return history
    
    def load_chain(self):
        """Load blockchain from file"""
        with open(self.chain_file, 'r') as f:
            return json.load(f)
    
    def save_chain(self, chain):
        """Save blockchain to file"""
        with open(self.chain_file, 'w') as f:
            json.dump(chain, f, indent=2)
    
    def validate_chain(self):
        """Validate blockchain integrity"""
        chain = self.load_chain()
        
        for i in range(1, len(chain)):
            current_block = chain[i]
            previous_block = chain[i-1]
            
            # Check hash continuity
            if current_block["previous_hash"] != previous_block["hash"]:
                return False, f"Chain broken at block {i}"
            
            # Recalculate hash to verify
            calculated_hash = self.calculate_hash(
                current_block["index"],
                current_block["timestamp"],
                current_block["access_grant"]
            )
            
            if calculated_hash != current_block["hash"]:
                return False, f"Invalid hash at block {i}"
        
        return True, "Chain is valid"

# Smart Contract-like functionality
class AccessContract:
    def __init__(self, blockchain):
        self.blockchain = blockchain
        self.contracts = self.load_contracts()
    
    def load_contracts(self):
        """Load smart contracts"""
        with open(self.blockchain.contracts_file, 'r') as f:
            return json.load(f)
    
    def patient_consent(self, patient_id, doctor_id, file_hash, duration_days=30):
        """Patient grants consent to doctor"""
        contract = self.contracts["patient_consent"]
        
        # Validate contract terms
        if duration_days > 90:
            return False, "Duration exceeds maximum allowed"
        
        # Add access grant
        block = self.blockchain.add_access_grant(
            doctor_id, patient_id, file_hash, 
            ["read"], duration_days
        )
        
        return True, f"Consent granted - Block {block['index']}"
    
    def doctor_request(self, doctor_id, patient_id, file_hash, justification):
        """Doctor requests access with justification"""
        contract = self.contracts["doctor_access"]
        
        # Validate doctor credentials (simplified)
        if not justification:
            return False, "Justification required"
        
        # In real system, verify license, department, etc.
        
        # Grant access
        block = self.blockchain.add_access_grant(
            doctor_id, patient_id, file_hash,
            ["read"], duration_days=7
        )
        
        return True, f"Access granted - Block {block['index']}"
    
    def emergency_access(self, doctor_id, patient_id, file_hash, urgency, reason):
        """Emergency break-glass access"""
        contract = self.contracts["emergency_access"]
        
        # Grant temporary access (1 hour)
        block = self.blockchain.add_access_grant(
            doctor_id, patient_id, file_hash,
            ["read", "download"], duration_days=0.04  # ~1 hour
        )
        
        # Schedule auto-revoke (in real system)
        
        return True, f"Emergency access granted - Block {block['index']}"

# Integration with Flask app
def integrate_blockchain_access(app):
    """Integrate blockchain access control with Flask app"""
    blockchain = BlockchainAccessControl()
    access_contract = AccessContract(blockchain)
    
    @app.route('/api/blockchain/grant_access', methods=['POST'])
    def grant_blockchain_access():
        """Grant access using blockchain"""
        data = request.get_json()
        doctor_id = data.get('doctor_id')
        patient_id = data.get('patient_id')
        file_hash = data.get('file_hash')
        duration = data.get('duration_days', 30)
        
        success, message = access_contract.patient_consent(
            patient_id, doctor_id, file_hash, duration
        )
        
        return jsonify({'success': success, 'message': message})
    
    @app.route('/api/blockchain/verify_access', methods=['POST'])
    def verify_blockchain_access():
        """Verify access using blockchain"""
        data = request.get_json()
        doctor_id = data.get('doctor_id')
        patient_id = data.get('patient_id')
        file_hash = data.get('file_hash')
        
        has_access, message = blockchain.verify_access(doctor_id, patient_id, file_hash)
        
        return jsonify({
            'has_access': has_access,
            'message': message
        })
    
    @app.route('/api/blockchain/history/<patient_id>', methods=['GET'])
    def get_patient_blockchain_history(patient_id):
        """Get patient's access history from blockchain"""
        history = blockchain.get_access_history(patient_id=patient_id)
        return jsonify({'history': history})
    
    @app.route('/api/blockchain/validate', methods=['GET'])
    def validate_blockchain():
        """Validate blockchain integrity"""
        is_valid, message = blockchain.validate_chain()
        return jsonify({'valid': is_valid, 'message': message})
    
    return blockchain, access_contract

if __name__ == "__main__":
    # Demo blockchain functionality
    bc = BlockchainAccessControl()
    ac = AccessContract(bc)
    
    print("HealthChain Blockchain Access Control Demo")
    print("=" * 50)
    
    # Patient grants consent
    success, msg = ac.patient_consent("pat_001", "doc_001", "file_hash_123", 30)
    print(f"Patient Consent: {msg}")
    
    # Doctor requests access
    success, msg = ac.doctor_request("doc_002", "pat_002", "file_hash_456", "Routine checkup")
    print(f"Doctor Request: {msg}")
    
    # Emergency access
    success, msg = ac.emergency_access("doc_001", "pat_002", "file_hash_789", "critical", "Cardiac emergency")
    print(f"Emergency Access: {msg}")
    
    # Verify access
    has_access, msg = bc.verify_access("doc_001", "pat_001", "file_hash_123")
    print(f"Access Verification: {msg}")
    
    # Validate chain
    is_valid, msg = bc.validate_chain()
    print(f"Chain Validation: {msg}")
    
    print("\nBlockchain access control system operational!")
