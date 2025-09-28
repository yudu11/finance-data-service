
```mermaid
graph TD
    Dev[👨‍💻 开发者] -->|push code| GitHub[(GitHub Repo)]
    GitHub -->|trigger CI/CD| CI[⚙️ GitHub Actions / Jenkins]

    CI -->|Terraform Apply| LocalStack[(🟧 LocalStack AWS 模拟)]
    CI -->|kubectl / helm| K3d[(☸️ k3d Kubernetes 集群)]

    LocalStack -->|模拟 AWS 服务| AWS[(S3, IAM, ECR, CloudWatch...)]
    K3d -->|运行应用容器| App[(🚀 应用服务 Pods)]

    Dev -->|kubectl port-forward / ingress| App
